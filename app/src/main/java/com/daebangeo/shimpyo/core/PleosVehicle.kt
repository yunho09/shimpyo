package com.daebangeo.shimpyo.core

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import android.content.Context
import com.daebangeo.shimpyo.domain.Powertrain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 쪽잠 세팅 체크리스트 항목 (화면에 ✓/✕로 보여줌) */
enum class SetupItem(val label: String) {
    LOCK("문 잠금"),
    VENT("창문 틈 환기"),
    LIGHTS("조명 끄기"),
    CLIMATE("수면 온도"),
}

sealed interface ItemStatus {
    data object Pending : ItemStatus
    data object Done : ItemStatus
    data class Failed(val reason: String) : ItemStatus
    /** 차가 지원하지 않아 사용자가 직접 해야 하는 경우 */
    data class Manual(val guide: String) : ItemStatus
    data class Skipped(val reason: String) : ItemStatus
}

/**
 * Pleos Vehicle SDK 래퍼.
 * 모든 호출은 비동기(onComplete/onFailed)이고, 결과는 [ApiLog]와 체크리스트에 남긴다.
 *
 * 문서 기준: Vehicle SDK 2.3.2
 * - Vehicle(context) → initialize() → getXxx() → ... → release()
 * - 콘솔에 "준비 중"이어도 공모전에서는 에뮬레이터 개발·시험 가능 (공모전 공지)
 */
class PleosVehicle(
    context: Context,
    private val log: ApiLog,
    private val scope: CoroutineScope,
) {
    private val vehicle = Vehicle(context)
    private var hvac: HVAC? = null
    private var door: Door? = null
    private var window: Window? = null
    private var light: Light? = null

    private val _cabinTemp = MutableStateFlow<Float?>(null)
    /** 실내 온도(℃): getHvacTemperatureCurrent = "현재 차량 내부 온도" (설정 온도 아님) */
    val cabinTemp: StateFlow<Float?> = _cabinTemp.asStateFlow()

    private val _doorLocked = MutableStateFlow<Boolean?>(null)
    val doorLocked: StateFlow<Boolean?> = _doorLocked.asStateFlow()

    private val _setup = MutableStateFlow<Map<SetupItem, ItemStatus>>(emptyMap())
    val setup: StateFlow<Map<SetupItem, ItemStatus>> = _setup.asStateFlow()

    private val driverSeat get() = SeatArea(SeatValue.ROW_1_LEFT)
    private val doorAreas = listOf(
        DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT,
    ).map { DoorArea(it) }

    private val tempListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(temperature: Float?) {
            _cabinTemp.value = temperature
        }
    }

    private val _driverDoorOpened = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** 운전석 문이 열릴 때마다 한 번씩 (쪽잠 중이면 끝낸다) */
    val driverDoorOpened: SharedFlow<Unit> = _driverDoorOpened.asSharedFlow()
    private var lastDoorPos: Int? = null
    private val driverDoor get() = DoorArea(DoorValue.ROW_1_LEFT)

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            val was = lastDoorPos
            lastDoorPos = pos
            if (pos != null && pos > 0 && (was == null || was == 0)) _driverDoorOpened.tryEmit(Unit)
        }
    }

    private var rampJob: Job? = null

    fun start() {
        log.guard("Vehicle.initialize") {
            vehicle.initialize()
            hvac = vehicle.getHVAC()
            door = vehicle.getDoor()
            window = vehicle.getWindow()
            light = vehicle.getLight()
            log.ok("Vehicle.initialize")
        }
        log.guard("HVAC.registerHvacTemperatureCurrent") {
            hvac?.registerHvacTemperatureCurrent(driverSeat, tempListener)
            log.ok("HVAC.registerHvacTemperatureCurrent")
        }
        log.guard("Door.registerDoorPosition") {
            door?.registerDoorPosition(driverDoor, doorListener)
            log.ok("Door.registerDoorPosition", "운전석")
        }
        readCabinTemp()
        readDoorLock()
    }

    fun stop() {
        rampJob?.cancel()
        log.guard("HVAC.unregisterHvacTemperatureCurrent") { hvac?.unregisterHvacTemperatureCurrent(driverSeat, tempListener) }
        log.guard("Door.unregisterDoorPosition") { door?.unregisterDoorPosition(driverDoor, doorListener) }
        log.guard("Vehicle.release") { vehicle.release() }
    }

    // ── 쪽잠 세팅 / 깨우기 ───────────────────────────────────────

    fun applyNapSetup(powertrain: Powertrain, sleepTempC: Float) {
        _setup.value = SetupItem.entries.associateWith { ItemStatus.Pending }
        lockDoors(true)
        openVent()
        dimLights()
        when (powertrain) {
            Powertrain.HYBRID -> sleepClimate(sleepTempC)
            Powertrain.GASOLINE -> mark(SetupItem.CLIMATE, ItemStatus.Skipped("시동을 끄고 쉬어서 공조는 쓰지 않아요"))
        }
    }

    /** rampLights=false면 이미 미리 밝혀 두었으므로 실내등·공조만 */
    fun applyWakeUp(powertrain: Powertrain, rampLights: Boolean) {
        if (rampLights) rampAmbientUp(QUICK_RAMP_MS) else setCabinLight(true)
        if (powertrain == Powertrain.HYBRID) setTemperature(WAKE_TEMP_C)
    }

    // ── 문 ───────────────────────────────────────────────────────

    fun lockDoors(lock: Boolean) {
        val d = door ?: return notReady("Door.setDoorLock", SetupItem.LOCK)
        var remaining = doorAreas.size
        var anyFail: String? = null
        doorAreas.forEach { area ->
            log.guard("Door.setDoorLock") {
                d.setDoorLock(
                    area, lock,
                    onComplete = { a, v ->
                        log.ok("Door.setDoorLock", "$a → $v")
                        remaining--
                        if (remaining == 0 && lock) mark(SetupItem.LOCK, anyFail?.let { ItemStatus.Failed(it) } ?: ItemStatus.Done)
                    },
                    onFailed = { e ->
                        log.fail("Door.setDoorLock($area)", e)
                        anyFail = e.message ?: "실패"
                        remaining--
                        if (remaining == 0 && lock) mark(SetupItem.LOCK, ItemStatus.Failed(anyFail ?: "실패"))
                    },
                )
            }
        }
    }

    fun readDoorLock() {
        val d = door ?: return
        log.guard("Door.getDoorLockState") {
            d.getDoorLockState(
                doorAreas.first(),
                onComplete = { v -> _doorLocked.value = v; log.ok("Door.getDoorLockState", "$v") },
                onFailed = { e -> log.fail("Door.getDoorLockState", e) },
            )
        }
    }

    // ── 창문 (환기 틈) ───────────────────────────────────────────

    /** 각 창문 범위의 10%만 연다. 범위가 0..1처럼 좁으면 "완전 열림"이 되므로 열지 않고 안내만. */
    fun openVent() = withWindowBounds { bounds ->
        val w = window ?: return@withWindowBounds
        var opened = 0
        bounds.forEach { (area, b) ->
            val range = b.maxValue - b.minValue
            if (range >= MIN_WINDOW_STEPS) {
                val target = b.minValue + maxOf(1, range / 10)
                setWindow(w, area, target)
                opened++
            }
        }
        mark(
            SetupItem.VENT,
            if (opened > 0) ItemStatus.Done else ItemStatus.Manual("창문을 손가락 한 마디만큼 열어 주세요"),
        )
    }

    fun closeVent() = withWindowBounds { bounds ->
        val w = window ?: return@withWindowBounds
        bounds.forEach { (area, b) -> setWindow(w, area, b.minValue) }
    }

    private fun setWindow(w: Window, area: WindowArea, value: Int) {
        log.guard("Window.setWindowPosition") {
            w.setWindowPosition(
                area, value,
                onComplete = { a, v -> log.ok("Window.setWindowPosition", "$a → $v") },
                onFailed = { e -> log.fail("Window.setWindowPosition($area)", e) },
            )
        }
    }

    private fun withWindowBounds(block: (Map<WindowArea, AreaBounds<Int>>) -> Unit) {
        val w = window ?: return notReady("Window.checkWindowPositionCapabilities", SetupItem.VENT)
        log.guard("Window.checkWindowPositionCapabilities") {
            w.checkWindowPositionCapabilities(
                onComplete = { _, map ->
                    log.ok("Window.checkWindowPositionCapabilities", map.entries.joinToString { "${it.key}=${it.value.minValue}..${it.value.maxValue}" })
                    block(map)
                },
                onFailed = { e ->
                    log.fail("Window.checkWindowPositionCapabilities", e)
                    mark(SetupItem.VENT, ItemStatus.Manual("창문을 손가락 한 마디만큼 열어 주세요"))
                },
            )
        }
    }

    // ── 조명 ─────────────────────────────────────────────────────

    fun setCabinLight(on: Boolean) {
        val l = light ?: return notReady("Light.setCabinLightSwitch", SetupItem.LIGHTS)
        log.guard("Light.setCabinLightSwitch") {
            l.setCabinLightSwitch(
                if (on) LightSwitch.ON else LightSwitch.OFF,
                onComplete = { v ->
                    log.ok("Light.setCabinLightSwitch", "$v")
                    if (!on) mark(SetupItem.LIGHTS, ItemStatus.Done)
                },
                onFailed = { e ->
                    log.fail("Light.setCabinLightSwitch", e)
                    if (!on) mark(SetupItem.LIGHTS, ItemStatus.Failed(e.message ?: "실패"))
                },
            )
        }
    }

    /** 실내등 끄고 무드등 최소로 */
    fun dimLights() {
        rampJob?.cancel()
        setCabinLight(false)
        withAmbientBounds { bounds -> bounds.forEach { (area, b) -> setAmbient(area, b.minValue) } }
    }

    /**
     * 무드등을 최소→최대로 durationMs 동안 서서히 올린다 (해 뜨듯이).
     * 끝나기 1분 전부터 시작하고, 다 밝아지면 깨울 때 실내등을 켠다.
     */
    fun rampAmbientUp(durationMs: Long = QUICK_RAMP_MS, cabinLightAtEnd: Boolean = true) {
        rampJob?.cancel()
        withAmbientBounds { bounds ->
            rampJob = scope.launch {
                val steps = 12
                val stepMs = (durationMs / steps).coerceAtLeast(250)
                for (i in 0..steps) {
                    bounds.forEach { (area, b) ->
                        val v = b.minValue + (b.maxValue - b.minValue) * i / steps
                        setAmbient(area, v)
                    }
                    if (i < steps) delay(stepMs)
                }
                if (cabinLightAtEnd) setCabinLight(true)
            }
        }
    }

    private fun setAmbient(area: SeatArea, value: Int) {
        val l = light ?: return
        log.guard("Light.setAmbientLightBrightness") {
            l.setAmbientLightBrightness(
                area, value,
                { a, v -> log.ok("Light.setAmbientLightBrightness", "$a → $v") },
                { e -> log.fail("Light.setAmbientLightBrightness", e) },
            )
        }
    }

    private fun withAmbientBounds(block: (Map<SeatArea, AreaBounds<Int>>) -> Unit) {
        val l = light ?: return
        log.guard("Light.checkAmbientLightBrightnessCapabilities") {
            l.checkAmbientLightBrightnessCapabilities(
                onComplete = { _, map ->
                    log.ok("Light.checkAmbientLightBrightnessCapabilities", map.entries.joinToString { "${it.key}=${it.value.minValue}..${it.value.maxValue}" })
                    block(map)
                },
                onFailed = { e -> log.fail("Light.checkAmbientLightBrightnessCapabilities", e) },
            )
        }
    }

    // ── 공조 ─────────────────────────────────────────────────────

    private fun sleepClimate(tempC: Float) {
        val h = hvac ?: return notReady("HVAC.setHvacPower", SetupItem.CLIMATE)
        log.guard("HVAC.setHvacPower") {
            h.setHvacPower(
                driverSeat, true,
                onComplete = { _, v -> log.ok("HVAC.setHvacPower", "$v") },
                onFailed = { e -> log.fail("HVAC.setHvacPower", e) },
            )
        }
        // 쪽잠 중 이산화탄소가 쌓이지 않도록 외기 유입(내기 순환 끔)
        log.guard("HVAC.setHvacRecirculation") {
            h.setHvacRecirculation(
                driverSeat, false,
                onComplete = { _, v -> log.ok("HVAC.setHvacRecirculation", "$v") },
                onFailed = { e -> log.fail("HVAC.setHvacRecirculation", e) },
            )
        }
        setTemperature(tempC, markClimate = true)
    }

    fun setHvacPower(on: Boolean) {
        val h = hvac ?: return
        log.guard("HVAC.setHvacPower") {
            h.setHvacPower(
                driverSeat, on,
                onComplete = { _, v -> log.ok("HVAC.setHvacPower", "$v") },
                onFailed = { e -> log.fail("HVAC.setHvacPower", e) },
            )
        }
    }

    fun setTemperature(tempC: Float, markClimate: Boolean = false) {
        val h = hvac ?: return notReady("HVAC.setHvacTemperature", if (markClimate) SetupItem.CLIMATE else null)
        log.guard("HVAC.setHvacTemperature") {
            h.setHvacTemperature(
                driverSeat, tempC, TemperatureUnit.CELSIUS,
                onComplete = { _, v ->
                    log.ok("HVAC.setHvacTemperature", "$v℃")
                    if (markClimate) mark(SetupItem.CLIMATE, ItemStatus.Done)
                },
                onFailed = { e ->
                    log.fail("HVAC.setHvacTemperature", e)
                    if (markClimate) mark(SetupItem.CLIMATE, ItemStatus.Failed(e.message ?: "실패"))
                },
            )
        }
    }

    fun readCabinTemp() {
        val h = hvac ?: return
        log.guard("HVAC.getHvacTemperatureCurrent") {
            h.getHvacTemperatureCurrent(
                driverSeat,
                onComplete = { v -> _cabinTemp.value = v; log.ok("HVAC.getHvacTemperatureCurrent", "$v℃") },
                onFailed = { e -> log.fail("HVAC.getHvacTemperatureCurrent", e) },
            )
        }
    }

    // ── 공통 ─────────────────────────────────────────────────────

    private fun mark(item: SetupItem, status: ItemStatus) = _setup.update { it + (item to status) }

    private fun notReady(api: String, item: SetupItem?) {
        log.failMsg(api, "Vehicle SDK 초기화 안 됨")
        if (item != null) mark(item, ItemStatus.Failed("SDK 초기화 안 됨"))
    }

    companion object {
        private const val WAKE_TEMP_C = 20f
        /** 바로 깨울 때 무드등 올리는 시간 */
        const val QUICK_RAMP_MS = 8_000L
        /** 이보다 범위가 좁으면 "틈"을 만들 수 없다고 본다 */
        private const val MIN_WINDOW_STEPS = 4
    }
}
