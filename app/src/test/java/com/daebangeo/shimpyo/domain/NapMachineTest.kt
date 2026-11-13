package com.daebangeo.shimpyo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class NapMachineTest {

    private val settings = NapSettings.demo(Powertrain.HYBRID) // 20초 운전 → 제안, 20초 쪽잠
    private val machine = NapMachine(settings)
    private val area = RestAreas.seed.first()

    private fun run(s: NapSnapshot, vararg events: NapEvent): Pair<NapSnapshot, List<NapEffect>> {
        var state = s
        val effects = mutableListOf<NapEffect>()
        for (e in events) {
            val t = machine.reduce(state, e)
            state = t.state
            effects += t.effects
        }
        return state to effects
    }

    /** 0초에 D, 1초 간격 Tick */
    private fun driveFor(seconds: Int): Pair<NapSnapshot, List<NapEffect>> {
        val events = mutableListOf<NapEvent>(NapEvent.Tick(0), NapEvent.GearChanged(Gear.DRIVE, 0))
        for (sec in 1..seconds) events += NapEvent.Tick(sec * 1000L)
        return run(NapSnapshot(), *events.toTypedArray())
    }

    private val parked = NapSnapshot(phase = Phase.PARKED, gear = Gear.PARK, lastTickMs = 0, parkedAtMs = 0)

    @Test
    fun `주행 시간이 기준을 넘으면 휴게소를 찾는다`() {
        val (s, fx) = driveFor(20)
        assertEquals(Phase.SUGGESTING, s.phase)
        assertTrue(fx.contains(NapEffect.FindRestArea))
    }

    @Test
    fun `기준 전에는 제안하지 않는다`() {
        val (s, fx) = driveFor(19)
        assertEquals(Phase.DRIVING, s.phase)
        assertTrue(fx.isEmpty())
    }

    @Test
    fun `운전 중 직접 쉴 곳을 찾을 수 있다`() {
        val (s0, _) = driveFor(5)
        val (s1, fx) = run(s0, NapEvent.RestRequested(6_000))
        assertEquals(Phase.SUGGESTING, s1.phase)
        assertTrue(fx.contains(NapEffect.FindRestArea))
        // 이미 제안 중이면 무시
        val (_, fx2) = run(s1, NapEvent.RestRequested(7_000))
        assertTrue(fx2.isEmpty())
    }

    @Test
    fun `P 구간은 주행 시간에 넣지 않는다`() {
        val (s, _) = run(
            NapSnapshot(),
            NapEvent.Tick(0),
            NapEvent.GearChanged(Gear.DRIVE, 0),
            NapEvent.Tick(5_000),
            NapEvent.GearChanged(Gear.PARK, 5_000),
            NapEvent.Tick(60_000),
        )
        assertEquals(5_000, s.drivenSinceRestMs)
    }

    @Test
    fun `휴게소를 찾으면 묻고, 응하면 경유지를 넣는다`() {
        val (s0, _) = driveFor(20)
        val (s1, fx1) = run(s0, NapEvent.RestAreaFound(area, 12.3, 20_000, ahead = true))
        assertEquals(area, s1.suggestedRestArea)
        assertTrue(fx1.any { it is NapEffect.Speak && it.text.contains("12km 앞") && it.text.contains(area.name) })
        assertTrue(fx1.contains(NapEffect.Listen(Question.REST)))

        val (s2, fx2) = run(s1, NapEvent.Answered(Answer.YES, 21_000))
        assertEquals(Phase.HEADING_TO_REST, s2.phase)
        assertTrue(fx2.contains(NapEffect.AddWaypoint(area)))
        assertTrue(fx2.any { it is NapEffect.Speak && it.text.contains("죽전휴게소를") })
    }

    @Test
    fun `거절하면 스누즈 후 다시 묻는다`() {
        val (s0, _) = driveFor(20)
        val (s1, _) = run(s0, NapEvent.RestAreaFound(area, 3.0, 20_000), NapEvent.Answered(Answer.NO, 21_000))
        assertEquals(Phase.DRIVING, s1.phase)
        assertEquals(21_000 + settings.snoozeMs, s1.snoozeUntilMs)

        val (s2, fx2) = run(s1, NapEvent.Tick(21_000 + settings.snoozeMs))
        assertEquals(Phase.SUGGESTING, s2.phase)
        assertTrue(fx2.contains(NapEffect.FindRestArea))
    }

    @Test
    fun `대답이 없으면 거절로 처리한다`() {
        val (s0, _) = driveFor(20)
        val (s1, _) = run(s0, NapEvent.RestAreaFound(area, 3.0, 20_000))
        val (s2, _) = run(s1, NapEvent.Tick(20_000 + settings.answerTimeoutMs))
        assertEquals(Phase.DRIVING, s2.phase)
        assertNull(s2.suggestedRestArea)
    }

    @Test
    fun `쪽잠 전체 흐름 - 세팅, 미리 밝히기, 깨우기, 출발, 기록, 도착 시각`() {
        val (s0, _) = driveFor(20)
        val (s1, _) = run(
            s0,
            NapEvent.RestAreaFound(area, 3.0, 20_000),
            NapEvent.Answered(Answer.YES, 21_000),
            NapEvent.Tick(22_000),
            NapEvent.GearChanged(Gear.PARK, 22_000),
        )
        assertEquals(Phase.PARKED, s1.phase)
        assertEquals(Question.NAP, s1.pendingQuestion)

        val (s2, fx2) = run(s1, NapEvent.Answered(Answer.NAP, 23_000))
        assertEquals(Phase.NAPPING, s2.phase)
        assertEquals(settings.napMs, s2.napLengthMs)
        assertTrue(fx2.contains(NapEffect.ApplyNapSetup(Powertrain.HYBRID, settings.sleepTempC)))
        assertTrue(fx2.contains(NapEffect.WhiteNoise(true)))

        // 끝나기 preWake 전: 무드등 서서히
        val preWakeAt = 23_000 + settings.napMs - settings.preWakeMs
        val (s3, fx3) = run(s2, NapEvent.Tick(preWakeAt))
        assertTrue(s3.preWakeStarted)
        assertEquals(listOf<NapEffect>(NapEffect.StartLightRamp(settings.preWakeMs)), fx3)

        // 끝: 이미 밝혔으니 rampLights=false
        val (s4, fx4) = run(s3, NapEvent.Tick(23_000 + settings.napMs))
        assertEquals(Phase.WAKING, s4.phase)
        assertTrue(fx4.contains(NapEffect.ApplyWakeUp(rampLights = false)))

        val (s5, fx5) = run(s4, NapEvent.GearChanged(Gear.DRIVE, 50_000))
        assertEquals(Phase.DRIVING, s5.phase)
        assertEquals(0, s5.drivenSinceRestMs)
        assertTrue(fx5.contains(NapEffect.CloseVent))
        assertTrue(fx5.contains(NapEffect.RequestEta))
        val record = fx5.filterIsInstance<NapEffect.SaveRecord>().single().record
        assertEquals(23_000, record.startedAtMs)
        assertEquals(settings.napMs, record.napMs) // 깬 시각까지만 센다
        assertEquals(22_000, record.drivenBeforeMs)

        val (s6, fx6) = run(s5, NapEvent.EtaArrived(3600, "부산", 50_000))
        assertEquals(false, s6.etaBriefPending)
        assertTrue(fx6.any { it is NapEffect.Speak && it.text.contains("부산까지") && it.text.contains("도착 예정") })
    }

    @Test
    fun `쪽잠 길이를 고를 수 있고 최대 길이로 자른다`() {
        val (s1, fx1) = run(parked, NapEvent.NapRequested(0, napMs = 10_000))
        assertEquals(10_000L, s1.napEndsAtMs)
        assertTrue(fx1.any { it is NapEffect.Speak && it.text.contains("10초 뒤에") })

        val (s2, _) = run(parked, NapEvent.NapRequested(0, napMs = 10 * 60_000L))
        assertEquals(settings.maxNapMs, s2.napEndsAtMs)
    }

    @Test
    fun `P가 아니면 쪽잠을 시작하지 않는다`() {
        val (s0, _) = driveFor(5)
        val (s1, fx) = run(s0, NapEvent.NapRequested(6_000))
        assertEquals(Phase.DRIVING, s1.phase)
        assertTrue(fx.single() is NapEffect.Speak)
    }

    @Test
    fun `직접 깨우면 무드등도 같이 밝힌다`() {
        val (s1, fx) = run(parked, NapEvent.NapRequested(0), NapEvent.WakeRequested(1_000))
        assertEquals(Phase.WAKING, s1.phase)
        assertTrue(fx.contains(NapEffect.ApplyWakeUp(rampLights = true)))
    }

    @Test
    fun `쪽잠 중 문을 열면 끝낸다`() {
        val (s1, fx) = run(parked, NapEvent.NapRequested(0), NapEvent.DoorOpened(3_000))
        assertEquals(Phase.WAKING, s1.phase)
        assertTrue(fx.any { it is NapEffect.Speak && it.text.contains("문이 열렸어요") })
        // 쪽잠 중이 아니면 무시
        val (s2, fx2) = run(parked, NapEvent.DoorOpened(1_000))
        assertEquals(Phase.PARKED, s2.phase)
        assertTrue(fx2.isEmpty())
    }

    @Test
    fun `깨우는 중 5분만 더 하면 다시 재우고 어둡게 한다`() {
        val (s1, _) = run(parked, NapEvent.NapRequested(0), NapEvent.WakeRequested(1_000))
        val (s2, fx2) = run(s1, NapEvent.Answered(Answer.EXTEND, 2_000))
        assertEquals(Phase.NAPPING, s2.phase)
        assertEquals(2_000 + settings.extendMs, s2.napEndsAtMs)
        assertTrue(fx2.contains(NapEffect.DimLights))
        assertTrue(fx2.contains(NapEffect.AdjustClimate(settings.sleepTempC)))
    }

    @Test
    fun `최대 길이를 넘겨 더 재우지 않는다`() {
        // demo: 기본 20초, 연장 10초, 최대 40초 → 연장 2번까지
        var (s, _) = run(parked, NapEvent.NapRequested(0))
        s = run(s, NapEvent.ExtendRequested(1_000)).first
        s = run(s, NapEvent.ExtendRequested(2_000)).first
        assertEquals(40_000L, s.napEndsAtMs)
        val (s3, fx3) = run(s, NapEvent.ExtendRequested(3_000))
        assertEquals(40_000L, s3.napEndsAtMs)
        assertTrue(fx3.single() is NapEffect.Speak)
    }

    @Test
    fun `쪽잠 중 너무 더우면 공조를 낮추되 1분에 한 번만`() {
        val napping = NapSnapshot(phase = Phase.NAPPING, gear = Gear.PARK, napEndsAtMs = 999_999)
        val (s1, fx1) = run(napping, NapEvent.CabinTemperature(27f, 0))
        assertEquals(listOf<NapEffect>(NapEffect.AdjustClimate(settings.sleepTempC - 1)), fx1)
        val (_, fx2) = run(s1, NapEvent.CabinTemperature(28f, 30_000))
        assertTrue(fx2.isEmpty())
    }

    @Test
    fun `가솔린은 공조를 건드리지 않고 시동 끄기를 안내한다`() {
        val gas = NapMachine(NapSettings.demo(Powertrain.GASOLINE))
        val t = gas.reduce(parked, NapEvent.NapRequested(0))
        assertTrue(t.effects.any { it is NapEffect.Speak && it.text.contains("시동을 끄고") })
        val t2 = gas.reduce(t.state, NapEvent.CabinTemperature(35f, 1_000))
        assertTrue(t2.effects.isEmpty())
        assertEquals(listOf(10_000L, 20_000L), NapSettings.demo(Powertrain.GASOLINE).napOptionsMs)
    }

    @Test
    fun `음성 대답 해석`() {
        assertEquals(Answer.YES, VoiceParser.parse("응"))
        assertEquals(Answer.YES, VoiceParser.parse("네 좋아요"))
        assertEquals(Answer.NO, VoiceParser.parse("아니 괜찮아"))
        assertEquals(Answer.NO, VoiceParser.parse("그냥 가자"))
        assertEquals(Answer.NAP, VoiceParser.parse("잘게"))
        assertEquals(Answer.EXTEND, VoiceParser.parse("5분만 더 잘래"))
        assertNull(VoiceParser.parse("음..."))
    }

    @Test
    fun `시간 표기와 조사`() {
        assertEquals("20초", label(20_000))
        assertEquals("20분", label(20 * 60_000L))
        assertEquals("2시간", label(120 * 60_000L))
        assertEquals("1시간 30분", label(90 * 60_000L))
        assertEquals("01:00", clockAfter(0, 3600, TimeZone.getTimeZone("UTC")))
        assertEquals("를", objectParticle("안성휴게소"))
        assertEquals("을", objectParticle("서울만남의광장"))
        assertEquals("로", directionParticle("안성휴게소"))
        assertEquals("으로", directionParticle("서울만남의광장"))
        assertEquals("로", directionParticle("서울"))
    }

    @Test
    fun `가장 가까운 휴게소`() {
        val n = RestAreas.nearest(37.0, 127.2)!!
        assertEquals("안성휴게소", n.restArea.name)
        assertTrue(n.distanceKm < 3)
    }

    @Test
    fun `진행 방향 앞쪽 휴게소를 고른다`() {
        // 안성휴게소와 망향휴게소 사이에서
        val lat = 36.95
        val lon = 127.20
        val south = RestAreas.nearestAhead(lat, lon, headingDeg = 180.0)!!
        assertEquals("망향휴게소", south.restArea.name)
        assertTrue(south.ahead)
        val north = RestAreas.nearestAhead(lat, lon, headingDeg = 0.0)!!
        assertEquals("안성휴게소", north.restArea.name)
        assertTrue(north.ahead)
        // 방향 모름 → 가장 가까운 곳
        assertEquals(false, RestAreas.nearestAhead(lat, lon, headingDeg = null)!!.ahead)
    }

    @Test
    fun `기록 탭 숫자`() {
        val day = 24 * 60 * 60 * 1000L
        val now = 30 * day
        val recs = listOf(now - 1 * day, now - 2 * day, now - 3 * day, now - 9 * day).map { NapRecord(it, 0, 60_000) }
        assertEquals(3 to 1, RecordStats.weekCounts(recs, now))
        assertEquals("지난 7일보다 2번 많아요", RecordStats.weekChange(3, 1))
        assertEquals("지난 7일과 같아요", RecordStats.weekChange(2, 2))
        assertNull(RecordStats.medianDrivenMs(emptyList()))
        assertEquals(60_000L, RecordStats.medianDrivenMs(recs))
    }

    @Test
    fun `휴식 타이밍 추천은 최근 기록 중앙값을 1~2시간으로 자른다`() {
        val min = 60_000L
        assertEquals(120 * min, RestAdvisor.recommendedRestAfterMs(emptyList()))
        val recs = listOf(80, 90, 100).mapIndexed { i, m -> NapRecord(i.toLong(), 20 * min, m * min) }
        assertEquals(90 * min, RestAdvisor.recommendedRestAfterMs(recs))
        val short = listOf(NapRecord(0, 0, 10 * min))
        assertEquals(60 * min, RestAdvisor.recommendedRestAfterMs(short))
    }
}
