package com.daebangeo.shimpyo.domain

/** 안드로이드·SDK에 의존하지 않는 순수 모델. 단위 테스트 대상. */

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class Powertrain { HYBRID, GASOLINE }

data class RestArea(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val road: String,
)

data class NapRecord(
    val startedAtMs: Long,
    val napMs: Long,
    val drivenBeforeMs: Long,
)

data class NapSettings(
    /** 마지막 휴식 이후 이만큼 운전하면 휴식을 제안 */
    val restAfterMs: Long,
    /** 기본 쪽잠 길이 */
    val napMs: Long,
    /** 고를 수 있는 쪽잠 길이 */
    val napOptionsMs: List<Long>,
    /** "5분만 더"를 포함한 쪽잠 최대 길이 */
    val maxNapMs: Long,
    /** 끝나기 이만큼 전부터 무드등을 서서히 밝힌다 */
    val preWakeMs: Long,
    /** 제안을 거절하면 이만큼 뒤에 다시 물어봄 */
    val snoozeMs: Long,
    /** "5분만 더" */
    val extendMs: Long,
    /** 쪽잠 없이 이만큼 세워 두면 휴식으로 인정 */
    val restCountsAfterParkedMs: Long,
    /** 제안 후 대답이 없으면 이만큼 뒤에 거절로 처리 */
    val answerTimeoutMs: Long,
    val powertrain: Powertrain,
    /** 쪽잠 공조 목표 온도(℃) */
    val sleepTempC: Float = 22f,
) {
    companion object {
        private const val SEC = 1_000L
        private const val MIN = 60 * SEC

        fun real(powertrain: Powertrain) = NapSettings(
            restAfterMs = 120 * MIN,
            napMs = 20 * MIN,
            // 가솔린은 시동을 끄고 쉬므로 공조 없이 짧게
            napOptionsMs = if (powertrain == Powertrain.HYBRID) listOf(10 * MIN, 20 * MIN, 30 * MIN) else listOf(10 * MIN, 20 * MIN),
            maxNapMs = if (powertrain == Powertrain.HYBRID) 40 * MIN else 20 * MIN,
            preWakeMs = 60 * SEC,
            snoozeMs = 30 * MIN,
            extendMs = 5 * MIN,
            restCountsAfterParkedMs = 15 * MIN,
            answerTimeoutMs = 30 * SEC,
            powertrain = powertrain,
        )

        /** PT 시연용: 2시간 → 20초, 20분 → 20초 */
        fun demo(powertrain: Powertrain) = NapSettings(
            restAfterMs = 20 * SEC,
            napMs = 20 * SEC,
            napOptionsMs = if (powertrain == Powertrain.HYBRID) listOf(10 * SEC, 20 * SEC, 30 * SEC) else listOf(10 * SEC, 20 * SEC),
            maxNapMs = if (powertrain == Powertrain.HYBRID) 40 * SEC else 20 * SEC,
            preWakeMs = 6 * SEC,
            snoozeMs = 15 * SEC,
            extendMs = 10 * SEC,
            restCountsAfterParkedMs = 15 * SEC,
            answerTimeoutMs = 20 * SEC,
            powertrain = powertrain,
        )
    }
}

enum class Phase {
    /** 출발 전 */
    READY,
    /** 주행 중, 지켜보는 중 */
    DRIVING,
    /** 휴게소 제안 중 (대답 기다림) */
    SUGGESTING,
    /** 휴게소를 경유지로 넣고 가는 중 */
    HEADING_TO_REST,
    /** 정차 (기어 P) */
    PARKED,
    /** 쪽잠 중 */
    NAPPING,
    /** 깨우는 중 / 출발 준비 */
    WAKING,
}

enum class Question { REST, NAP }

enum class Answer { YES, NO, NAP, EXTEND }

data class NapSnapshot(
    val phase: Phase = Phase.READY,
    val gear: Gear = Gear.UNKNOWN,
    /** 마지막 휴식 이후 누적 주행 시간 (기어 P 구간 제외) */
    val drivenSinceRestMs: Long = 0,
    val lastTickMs: Long? = null,
    val snoozeUntilMs: Long = 0,
    val suggestedRestArea: RestArea? = null,
    val suggestedDistanceKm: Double? = null,
    val suggestedAhead: Boolean = false,
    val askedAtMs: Long? = null,
    val parkedAtMs: Long? = null,
    val napStartedAtMs: Long? = null,
    val napEndsAtMs: Long? = null,
    /** 이번 쪽잠의 처음 길이 (진행 표시용) */
    val napLengthMs: Long = 0,
    val wokeAtMs: Long? = null,
    val preWakeStarted: Boolean = false,
    val napDrivenBeforeMs: Long = 0,
    val etaBriefPending: Boolean = false,
    val lastClimateNudgeMs: Long? = null,
    val pendingQuestion: Question? = null,
)

sealed interface NapEvent {
    data class Tick(val nowMs: Long) : NapEvent
    data class GearChanged(val gear: Gear, val nowMs: Long) : NapEvent
    data class RestAreaFound(
        val restArea: RestArea?,
        val distanceKm: Double?,
        val nowMs: Long,
        /** 진행 방향 앞쪽에 있는지 ("6km 앞" / "6km 거리") */
        val ahead: Boolean = false,
    ) : NapEvent
    data class Answered(val answer: Answer, val nowMs: Long) : NapEvent
    /** 운전 중 "지금 쉴 곳 찾기" (기준 시간 전이라도) */
    data class RestRequested(val nowMs: Long) : NapEvent
    /** napMs가 null이면 기본 길이 */
    data class NapRequested(val nowMs: Long, val napMs: Long? = null) : NapEvent
    data class WakeRequested(val nowMs: Long) : NapEvent
    data class ExtendRequested(val nowMs: Long) : NapEvent
    data class DoorOpened(val nowMs: Long) : NapEvent
    data class EtaArrived(val remainSec: Int?, val destinationName: String?, val nowMs: Long) : NapEvent
    data class CabinTemperature(val celsius: Float, val nowMs: Long) : NapEvent
}

sealed interface NapEffect {
    data class Speak(val text: String) : NapEffect
    data class Listen(val question: Question) : NapEffect
    data object FindRestArea : NapEffect
    data class AddWaypoint(val restArea: RestArea) : NapEffect
    data class ApplyNapSetup(val powertrain: Powertrain, val sleepTempC: Float) : NapEffect
    /** 끝나기 전에 무드등을 서서히 밝히기 시작 */
    data class StartLightRamp(val durationMs: Long) : NapEffect
    /** 다시 어둡게 ("5분만 더") */
    data object DimLights : NapEffect
    /** rampLights=false면 이미 밝혀 두었으니 실내등·공조만 */
    data class ApplyWakeUp(val rampLights: Boolean) : NapEffect
    data object CloseVent : NapEffect
    data class WhiteNoise(val on: Boolean) : NapEffect
    data class AdjustClimate(val targetC: Float) : NapEffect
    data object RequestEta : NapEffect
    data class SaveRecord(val record: NapRecord) : NapEffect
}

data class Transition(val state: NapSnapshot, val effects: List<NapEffect> = emptyList())
