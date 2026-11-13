package com.daebangeo.shimpyo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daebangeo.shimpyo.core.ApiLog
import com.daebangeo.shimpyo.core.GearMonitor
import com.daebangeo.shimpyo.core.LocalStore
import com.daebangeo.shimpyo.core.NaviGateway
import com.daebangeo.shimpyo.core.PleosVehicle
import com.daebangeo.shimpyo.core.VoiceGuide
import com.daebangeo.shimpyo.core.VoiceListener
import com.daebangeo.shimpyo.core.WhiteNoisePlayer
import com.daebangeo.shimpyo.domain.Answer
import com.daebangeo.shimpyo.domain.Gear
import com.daebangeo.shimpyo.domain.NapEffect
import com.daebangeo.shimpyo.domain.NapEvent
import com.daebangeo.shimpyo.domain.NapMachine
import com.daebangeo.shimpyo.domain.NapRecord
import com.daebangeo.shimpyo.domain.NapSettings
import com.daebangeo.shimpyo.domain.NapSnapshot
import com.daebangeo.shimpyo.domain.Powertrain
import com.daebangeo.shimpyo.domain.RestAdvisor
import com.daebangeo.shimpyo.domain.RestAreas
import com.daebangeo.shimpyo.domain.VoiceParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 상태 머신([NapMachine])과 차량·내비·음성을 잇는 곳.
 * 상태 변화는 전부 메인 스레드에서 [dispatch]로만 일어난다.
 */
class ShimpyoViewModel(app: Application) : AndroidViewModel(app) {

    val log = ApiLog()
    private val store = LocalStore(app)

    val gearMonitor = GearMonitor(app, log)
    val vehicle = PleosVehicle(app, log, viewModelScope)
    val navi = NaviGateway(app, log)
    val voice = VoiceGuide(app, log)
    val ear = VoiceListener(app, log)
    private val noise = WhiteNoisePlayer(log)

    private val _demoMode = MutableStateFlow(store.demoMode)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val _powertrain = MutableStateFlow(store.powertrain)
    val powertrain: StateFlow<Powertrain> = _powertrain.asStateFlow()

    private val _records = MutableStateFlow(store.records())
    val records: StateFlow<List<NapRecord>> = _records.asStateFlow()

    private val machine = NapMachine(buildSettings())

    private val _snapshot = MutableStateFlow(NapSnapshot())
    val snapshot: StateFlow<NapSnapshot> = _snapshot.asStateFlow()

    val settings: NapSettings get() = machine.settings

    private var listenPending = false
    private var listenFallback: Job? = null
    /** 못 알아들었을 때 한 번만 다시 묻는다 */
    private var listenRetries = 0

    init {
        gearMonitor.start()
        vehicle.start()
        navi.start()
        voice.start()
        ear.start()

        voice.onDone = { viewModelScope.launch { startListeningIfPending() } }
        ear.onFinal = { text -> viewModelScope.launch { onHeard(text) } }

        viewModelScope.launch {
            gearMonitor.gear.collect { dispatch(NapEvent.GearChanged(it, now())) }
        }
        viewModelScope.launch {
            vehicle.cabinTemp.filterNotNull().collect { dispatch(NapEvent.CabinTemperature(it, now())) }
        }
        viewModelScope.launch {
            vehicle.driverDoorOpened.collect { dispatch(NapEvent.DoorOpened(now())) }
        }
        viewModelScope.launch {
            while (isActive) {
                dispatch(NapEvent.Tick(now()))
                delay(1_000)
            }
        }
    }

    // ── 화면에서 부르는 것들 ─────────────────────────────────────

    fun answer(answer: Answer) {
        ear.cancel()
        listenPending = false
        dispatch(NapEvent.Answered(answer, now()))
    }

    /** napMs가 null이면 기본 길이 */
    fun requestNap(napMs: Long? = null) {
        ear.cancel()
        listenPending = false
        dispatch(NapEvent.NapRequested(now(), napMs))
    }
    fun requestWake() = dispatch(NapEvent.WakeRequested(now()))
    /** 운전 중 "지금 쉴 곳 찾기" */
    fun requestRest() = dispatch(NapEvent.RestRequested(now()))
    fun requestExtend() = dispatch(NapEvent.ExtendRequested(now()))

    fun setDemoMode(on: Boolean) {
        store.demoMode = on
        _demoMode.value = on
        machine.settings = buildSettings()
    }

    fun setPowertrain(p: Powertrain) {
        store.powertrain = p
        _powertrain.value = p
        machine.settings = buildSettings()
    }

    fun clearRecords() {
        store.clearRecords()
        _records.value = emptyList()
        machine.settings = buildSettings()
    }

    fun testSpeak() = voice.speak("쉼표 음성 안내 테스트예요. 들리시나요?")
    fun testListen() = ear.listen()

    // ── 상태 머신 ────────────────────────────────────────────────

    private fun dispatch(event: NapEvent) {
        val t = machine.reduce(_snapshot.value, event)
        _snapshot.value = t.state
        t.effects.forEach(::execute)
    }

    private fun execute(effect: NapEffect) {
        when (effect) {
            is NapEffect.Speak -> voice.speak(effect.text)

            is NapEffect.Listen -> {
                listenRetries = 0
                listenAfterSpeech()
            }

            NapEffect.FindRestArea -> viewModelScope.launch {
                val loc = navi.freshLocation(2_500)
                val lat = loc?.latitude ?: if (_demoMode.value) DEMO_LAT else null
                val lon = loc?.longitude ?: if (_demoMode.value) DEMO_LON else null
                // heading 0.0은 기본값일 수 있어 "방향 모름"으로 본다. 시연 위치는 남쪽(부산 방향)으로 간다고 가정.
                val heading = loc?.heading?.takeIf { it != 0.0 } ?: if (loc == null && _demoMode.value) DEMO_HEADING else null
                val nearest = if (lat != null && lon != null) RestAreas.nearestAhead(lat, lon, heading) else null
                if (loc == null && nearest != null) log.ok("휴게소 찾기", "위치 없음 → 시연 위치 사용")
                dispatch(NapEvent.RestAreaFound(nearest?.restArea, nearest?.distanceKm, now(), ahead = nearest?.ahead == true))
            }

            is NapEffect.AddWaypoint -> navi.goVia(effect.restArea)

            is NapEffect.ApplyNapSetup -> vehicle.applyNapSetup(effect.powertrain, effect.sleepTempC)

            is NapEffect.StartLightRamp -> vehicle.rampAmbientUp(effect.durationMs, cabinLightAtEnd = false)

            NapEffect.DimLights -> vehicle.dimLights()

            is NapEffect.ApplyWakeUp -> vehicle.applyWakeUp(_powertrain.value, effect.rampLights)

            NapEffect.CloseVent -> vehicle.closeVent()

            is NapEffect.WhiteNoise -> if (effect.on) noise.start() else noise.stop()

            is NapEffect.AdjustClimate -> vehicle.setTemperature(effect.targetC)

            NapEffect.RequestEta -> viewModelScope.launch {
                val d = navi.freshDestination(2_500)
                dispatch(NapEvent.EtaArrived(d?.remainTime, d?.name, now()))
            }

            is NapEffect.SaveRecord -> {
                _records.value = store.addRecord(effect.record)
                if (!_demoMode.value) machine.settings = buildSettings()
            }
        }
    }

    /** 질문을 다 말한 뒤에 듣는다 (TTS onDone). onDone이 안 오면 6초 뒤에 듣기 시작. */
    private fun listenAfterSpeech() {
        listenPending = true
        listenFallback?.cancel()
        listenFallback = viewModelScope.launch {
            delay(6_000)
            startListeningIfPending()
        }
    }

    private fun startListeningIfPending() {
        if (!listenPending) return
        listenPending = false
        listenFallback?.cancel()
        ear.listen()
    }

    private fun onHeard(text: String) {
        val answer = VoiceParser.parse(text)
        if (answer != null) {
            dispatch(NapEvent.Answered(answer, now()))
            return
        }
        val waitingForAnswer = _snapshot.value.pendingQuestion != null
        if (waitingForAnswer && listenRetries < 1) {
            listenRetries++
            voice.speak("잘 못 들었어요. 한 번만 더 말씀해 주세요.")
            listenAfterSpeech()
        } else if (waitingForAnswer) {
            voice.speak("화면에서 골라 주세요.")
        }
    }

    private fun buildSettings(): NapSettings {
        val p = _powertrain.value
        return if (_demoMode.value) {
            NapSettings.demo(p)
        } else {
            NapSettings.real(p).copy(restAfterMs = RestAdvisor.recommendedRestAfterMs(_records.value))
        }
    }

    private fun now() = System.currentTimeMillis()

    fun simulateGear(gear: Gear) = gearMonitor.simulate(gear)

    override fun onCleared() {
        noise.stop()
        ear.stop()
        voice.stop()
        navi.stop()
        vehicle.stop()
        gearMonitor.stop()
        super.onCleared()
    }

    companion object {
        /** 시연 모드에서 위치를 못 받으면 쓰는 위치 (경부고속도로 안성 부근) */
        private const val DEMO_LAT = 37.05
        private const val DEMO_LON = 127.18
        private const val DEMO_HEADING = 180.0
    }
}
