package com.daebangeo.shimpyo.domain

import kotlin.math.roundToInt

/**
 * 쉼표 쪽잠 흐름 상태 머신 (순수 함수).
 *
 * 주행 2시간 → 진행 방향 휴게소 제안 → "응" → 경유지 추가 → 기어 P → "쪽잠 할까요?" → "잘게"(10·20·30분)
 * → 잠금·창문 틈·조명 끄기·수면 온도 → 끝나기 1분 전 무드등 서서히 → 깨우기 (문을 열어도 끝)
 * → 출발(기어 D) → 도착 시각 안내 → 기록
 *
 * 안드로이드·SDK 호출은 하지 않고, 해야 할 일을 [NapEffect]로 돌려준다.
 */
class NapMachine(var settings: NapSettings) {

    fun reduce(s: NapSnapshot, e: NapEvent): Transition = when (e) {
        is NapEvent.Tick -> onTick(s, e.nowMs)
        is NapEvent.GearChanged -> onGear(s, e.gear, e.nowMs)
        is NapEvent.RestAreaFound -> onRestArea(s, e)
        is NapEvent.Answered -> onAnswer(s, e.answer, e.nowMs)
        is NapEvent.RestRequested ->
            if (s.phase == Phase.DRIVING) {
                Transition(
                    s.copy(phase = Phase.SUGGESTING, suggestedRestArea = null, askedAtMs = e.nowMs),
                    listOf(NapEffect.FindRestArea),
                )
            } else Transition(s)
        is NapEvent.NapRequested -> startNap(s, e.nowMs, e.napMs)
        is NapEvent.WakeRequested -> wake(s, e.nowMs, WAKE_TEXT)
        is NapEvent.ExtendRequested -> extend(s, e.nowMs)
        is NapEvent.DoorOpened -> wake(s, e.nowMs, "문이 열렸어요. 쪽잠을 마칠게요. 준비되면 출발하세요.")
        is NapEvent.EtaArrived -> onEta(s, e)
        is NapEvent.CabinTemperature -> onCabinTemp(s, e.celsius, e.nowMs)
    }

    // ── 시간 흐름 ────────────────────────────────────────────────

    private fun onTick(s0: NapSnapshot, now: Long): Transition {
        val s = accumulate(s0, now)
        return when (s.phase) {
            Phase.DRIVING ->
                if (s.drivenSinceRestMs >= settings.restAfterMs && now >= s.snoozeUntilMs) {
                    Transition(
                        s.copy(phase = Phase.SUGGESTING, suggestedRestArea = null, askedAtMs = now),
                        listOf(NapEffect.FindRestArea),
                    )
                } else Transition(s)

            Phase.SUGGESTING -> {
                val asked = s.askedAtMs ?: now
                if (now - asked >= settings.answerTimeoutMs) snooze(s, now, speak = false) else Transition(s)
            }

            Phase.NAPPING -> {
                val ends = s.napEndsAtMs ?: return Transition(s)
                when {
                    now >= ends -> wake(s, now, WAKE_TEXT)
                    !s.preWakeStarted && ends - now <= settings.preWakeMs -> Transition(
                        s.copy(preWakeStarted = true),
                        listOf(NapEffect.StartLightRamp(ends - now)),
                    )
                    else -> Transition(s)
                }
            }

            else -> Transition(s)
        }
    }

    /** 기어가 P가 아닐 때만 주행 시간으로 센다. */
    private fun accumulate(s: NapSnapshot, now: Long): NapSnapshot {
        val last = s.lastTickMs ?: return s.copy(lastTickMs = now)
        val delta = (now - last).coerceAtLeast(0)
        val moving = s.gear == Gear.DRIVE || s.gear == Gear.REVERSE || s.gear == Gear.NEUTRAL
        return s.copy(
            lastTickMs = now,
            drivenSinceRestMs = if (moving) s.drivenSinceRestMs + delta else s.drivenSinceRestMs,
        )
    }

    // ── 기어 ─────────────────────────────────────────────────────

    private fun onGear(s0: NapSnapshot, gear: Gear, now: Long): Transition {
        // 기어가 바뀌기 직전까지의 주행 시간을 먼저 반영
        val s = accumulate(s0, now).copy(gear = gear)
        if (gear == Gear.UNKNOWN) return Transition(s)
        return if (gear == Gear.PARK) onParked(s, now) else onMoving(s, now)
    }

    private fun onParked(s: NapSnapshot, now: Long): Transition = when (s.phase) {
        Phase.READY, Phase.PARKED, Phase.NAPPING, Phase.WAKING -> Transition(s)

        Phase.DRIVING, Phase.SUGGESTING, Phase.HEADING_TO_REST -> {
            val wasHeading = s.phase == Phase.HEADING_TO_REST
            val tired = s.drivenSinceRestMs >= settings.restAfterMs
            val parked = s.copy(
                phase = Phase.PARKED,
                parkedAtMs = now,
                suggestedRestArea = if (wasHeading) s.suggestedRestArea else null,
                askedAtMs = null,
                pendingQuestion = null,
            )
            if (wasHeading || tired) {
                Transition(
                    parked.copy(pendingQuestion = Question.NAP),
                    listOf(
                        NapEffect.Speak("수고하셨어요. 여기서 ${label(settings.napMs)} 쪽잠 할까요?"),
                        NapEffect.Listen(Question.NAP),
                    ),
                )
            } else Transition(parked)
        }
    }

    private fun onMoving(s: NapSnapshot, now: Long): Transition = when (s.phase) {
        Phase.READY -> Transition(s.copy(phase = Phase.DRIVING))

        Phase.PARKED -> {
            val parkedFor = now - (s.parkedAtMs ?: now)
            val rested = parkedFor >= settings.restCountsAfterParkedMs
            Transition(
                s.copy(
                    phase = Phase.DRIVING,
                    parkedAtMs = null,
                    pendingQuestion = null,
                    suggestedRestArea = null,
                    drivenSinceRestMs = if (rested) 0 else s.drivenSinceRestMs,
                    snoozeUntilMs = if (rested) 0 else s.snoozeUntilMs,
                ),
            )
        }

        Phase.NAPPING, Phase.WAKING -> depart(s, now)

        Phase.DRIVING, Phase.SUGGESTING, Phase.HEADING_TO_REST -> Transition(s)
    }

    private fun depart(s: NapSnapshot, now: Long): Transition {
        val started = s.napStartedAtMs ?: now
        val ended = s.wokeAtMs ?: now
        val record = NapRecord(
            startedAtMs = started,
            napMs = (ended - started).coerceAtLeast(0),
            drivenBeforeMs = s.napDrivenBeforeMs,
        )
        val effects = buildList {
            if (s.phase == Phase.NAPPING) add(NapEffect.WhiteNoise(false))
            add(NapEffect.CloseVent)
            add(NapEffect.RequestEta)
            add(NapEffect.SaveRecord(record))
        }
        return Transition(
            s.copy(
                phase = Phase.DRIVING,
                drivenSinceRestMs = 0,
                snoozeUntilMs = 0,
                parkedAtMs = null,
                napStartedAtMs = null,
                napEndsAtMs = null,
                napLengthMs = 0,
                wokeAtMs = null,
                preWakeStarted = false,
                napDrivenBeforeMs = 0,
                etaBriefPending = true,
                pendingQuestion = null,
                suggestedRestArea = null,
            ),
            effects,
        )
    }

    // ── 휴게소 제안 ──────────────────────────────────────────────

    private fun onRestArea(s: NapSnapshot, e: NapEvent.RestAreaFound): Transition {
        if (s.phase != Phase.SUGGESTING) return Transition(s)
        val area = e.restArea
        val drove = label(s.drivenSinceRestMs)
        return if (area != null) {
            val where = e.distanceKm?.let {
                val km = it.roundToInt().coerceAtLeast(1)
                if (e.ahead) "${km}km 앞 " else "${km}km 거리 "
            } ?: ""
            Transition(
                s.copy(
                    suggestedRestArea = area,
                    suggestedDistanceKm = e.distanceKm,
                    suggestedAhead = e.ahead,
                    askedAtMs = e.nowMs,
                    pendingQuestion = Question.REST,
                ),
                listOf(
                    NapEffect.Speak("$drove 운전하셨어요. $where${area.name}에서 ${label(settings.napMs)} 쉬어갈까요?"),
                    NapEffect.Listen(Question.REST),
                ),
            )
        } else {
            val snoozed = snooze(s, e.nowMs, speak = false)
            Transition(
                snoozed.state,
                listOf(NapEffect.Speak("$drove 운전하셨어요. 가까운 졸음쉼터나 휴게소에서 잠깐 쉬어가세요.")),
            )
        }
    }

    private fun snooze(s: NapSnapshot, now: Long, speak: Boolean): Transition {
        val next = s.copy(
            phase = Phase.DRIVING,
            snoozeUntilMs = now + settings.snoozeMs,
            suggestedRestArea = null,
            suggestedDistanceKm = null,
            askedAtMs = null,
            pendingQuestion = null,
        )
        val effects = if (speak) {
            listOf(NapEffect.Speak("알겠어요. ${label(settings.snoozeMs)} 뒤에 다시 여쭤볼게요."))
        } else emptyList()
        return Transition(next, effects)
    }

    // ── 대답 (음성 또는 버튼) ─────────────────────────────────────

    private fun onAnswer(s: NapSnapshot, answer: Answer, now: Long): Transition = when (s.phase) {
        Phase.SUGGESTING -> when (answer) {
            Answer.YES, Answer.NAP -> {
                val area = s.suggestedRestArea
                if (area == null) Transition(s)
                else Transition(
                    s.copy(phase = Phase.HEADING_TO_REST, askedAtMs = null, pendingQuestion = null),
                    listOf(
                        NapEffect.AddWaypoint(area),
                        NapEffect.Speak("${area.name}${objectParticle(area.name)} 경유지로 넣었어요. 도착하면 쪽잠 자리를 만들어 드릴게요."),
                    ),
                )
            }
            Answer.NO -> snooze(s, now, speak = true)
            Answer.EXTEND -> Transition(s)
        }

        Phase.PARKED -> when (answer) {
            Answer.YES, Answer.NAP -> startNap(s, now, null)
            Answer.NO -> Transition(s.copy(pendingQuestion = null), listOf(NapEffect.Speak("네, 편히 쉬세요.")))
            Answer.EXTEND -> Transition(s)
        }

        Phase.NAPPING, Phase.WAKING -> if (answer == Answer.EXTEND) extend(s, now) else Transition(s)

        else -> Transition(s)
    }

    // ── 쪽잠 ─────────────────────────────────────────────────────

    private fun startNap(s: NapSnapshot, now: Long, requestedMs: Long?): Transition {
        if (s.gear != Gear.PARK) {
            return Transition(s, listOf(NapEffect.Speak("차를 세우고 기어를 P에 놓으면 쪽잠을 시작할 수 있어요.")))
        }
        if (s.phase == Phase.NAPPING) return Transition(s)

        val length = (requestedMs ?: settings.napMs).coerceIn(1_000L, settings.maxNapMs)
        val guide = when (settings.powertrain) {
            Powertrain.HYBRID -> "하이브리드는 스테이 모드를 켜 두면 공조가 유지돼요."
            Powertrain.GASOLINE -> "공회전은 일산화탄소 위험이 있으니 시동을 끄고 쉬어 주세요."
        }
        return Transition(
            s.copy(
                phase = Phase.NAPPING,
                napStartedAtMs = now,
                napEndsAtMs = now + length,
                napLengthMs = length,
                wokeAtMs = null,
                preWakeStarted = false,
                napDrivenBeforeMs = s.drivenSinceRestMs,
                pendingQuestion = null,
                lastClimateNudgeMs = null,
            ),
            listOf(
                NapEffect.ApplyNapSetup(settings.powertrain, settings.sleepTempC),
                NapEffect.Speak("쪽잠 자리를 만들었어요. 문을 잠그고 창문을 조금 열었어요. $guide ${label(length)} 뒤에 깨워 드릴게요."),
                NapEffect.WhiteNoise(true),
            ),
        )
    }

    private fun wake(s: NapSnapshot, now: Long, text: String): Transition {
        if (s.phase != Phase.NAPPING) return Transition(s)
        return Transition(
            s.copy(phase = Phase.WAKING, napEndsAtMs = null, wokeAtMs = now),
            listOf(
                NapEffect.WhiteNoise(false),
                NapEffect.ApplyWakeUp(rampLights = !s.preWakeStarted),
                NapEffect.Speak(text),
            ),
        )
    }

    private fun extend(s: NapSnapshot, now: Long): Transition {
        if (s.phase != Phase.NAPPING && s.phase != Phase.WAKING) return Transition(s)
        val started = s.napStartedAtMs ?: now
        val base = if (s.phase == Phase.NAPPING) (s.napEndsAtMs ?: now) else now
        val newEnds = base + settings.extendMs
        if (newEnds - started > settings.maxNapMs) {
            return Transition(
                s,
                listOf(NapEffect.Speak("${label(settings.maxNapMs)}보다 길게 자면 깨어날 때 더 멍할 수 있어요. 오늘은 여기까지 쉬어 가요.")),
            )
        }
        val climate = if (settings.powertrain == Powertrain.HYBRID && s.phase == Phase.WAKING) {
            listOf(NapEffect.AdjustClimate(settings.sleepTempC))
        } else emptyList()

        return when (s.phase) {
            Phase.NAPPING -> Transition(
                s.copy(napEndsAtMs = newEnds, preWakeStarted = false),
                if (s.preWakeStarted) listOf(NapEffect.DimLights) else emptyList(),
            )
            else -> Transition(
                s.copy(phase = Phase.NAPPING, napEndsAtMs = newEnds, wokeAtMs = null, preWakeStarted = false),
                listOf(NapEffect.DimLights, NapEffect.WhiteNoise(true)) + climate +
                    NapEffect.Speak("${label(settings.extendMs)}만 더 주무세요."),
            )
        }
    }

    // ── 출발 브리핑 ──────────────────────────────────────────────

    private fun onEta(s: NapSnapshot, e: NapEvent.EtaArrived): Transition {
        if (!s.etaBriefPending) return Transition(s)
        val remain = e.remainSec
        val text = if (remain != null && remain > 0) {
            val dest = e.destinationName?.takeIf { it.isNotBlank() }?.let { "${it}까지 " } ?: ""
            "다시 출발해요. 지금 출발하면 ${dest}${clockAfter(e.nowMs, remain)} 도착 예정이에요."
        } else {
            "다시 출발해요. 안전운전하세요."
        }
        return Transition(s.copy(etaBriefPending = false), listOf(NapEffect.Speak(text)))
    }

    // ── 쪽잠 중 실내 온도 지키기 ──────────────────────────────────

    private fun onCabinTemp(s: NapSnapshot, celsius: Float, now: Long): Transition {
        if (s.phase != Phase.NAPPING) return Transition(s)
        if (settings.powertrain == Powertrain.GASOLINE) return Transition(s) // 시동 끔 → 공조 조절 안 함
        val last = s.lastClimateNudgeMs
        if (last != null && now - last < CLIMATE_NUDGE_COOLDOWN_MS) return Transition(s)

        val target = settings.sleepTempC
        val nudge = when {
            celsius >= target + 4 -> target - 1
            celsius <= target - 6 -> target + 1
            else -> null
        } ?: return Transition(s)

        return Transition(s.copy(lastClimateNudgeMs = now), listOf(NapEffect.AdjustClimate(nudge)))
    }

    companion object {
        const val CLIMATE_NUDGE_COOLDOWN_MS = 60_000L
        private const val WAKE_TEXT = "일어날 시간이에요. 불을 천천히 켜고 시원하게 바꿀게요. 준비되면 출발하세요."
    }
}
