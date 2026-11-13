package com.daebangeo.shimpyo.core

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.daebangeo.shimpyo.domain.Gear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 기어 + 주행 제한 상태 구독.
 * - 기어: Pleos Vehicle SDK에 기어 도메인이 없어서 AAOS 표준 CarPropertyManager(GEAR_SELECTION)
 * - 주행 제한: Pleos 가이드대로 CarUxRestrictions로 "지금 화면을 줄여야 하는지" 확인
 * 에뮬레이터에서는 Virtual Console(http://127.0.0.1:8088) → Motion → Gear 로 바꾼다.
 */
class GearMonitor(private val context: Context, private val log: ApiLog) {

    private val _gear = MutableStateFlow(Gear.UNKNOWN)
    val gear: StateFlow<Gear> = _gear.asStateFlow()

    private val _restricted = MutableStateFlow(false)
    /** true면 주행 중 화면 제한이 걸린 상태 (큰 글씨 + 음성 위주 화면으로) */
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val raw = value.value as? Int ?: return
            _gear.value = map(raw)
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            log.failMsg("GEAR_SELECTION", "onErrorEvent zone=$zone")
        }
    }

    fun start() {
        log.guard("Car.createCar") {
            car = Car.createCar(context) ?: error("Car.createCar returned null")
        }
        val c = car ?: return

        log.guard("CarPropertyManager(GEAR_SELECTION)") {
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            val ok = m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            if (ok) log.ok("CarPropertyManager.registerCallback(GEAR_SELECTION)")
            else log.failMsg("CarPropertyManager.registerCallback(GEAR_SELECTION)", "false 반환 (권한·지원 여부 확인)")
        }

        log.guard("CarUxRestrictionsManager") {
            val u = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            ux = u
            _restricted.value = u.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            u.registerListener { r -> _restricted.value = r.isRequiresDistractionOptimization }
            log.ok("CarUxRestrictionsManager.registerListener", "현재 제한=${_restricted.value}")
        }
    }

    fun stop() {
        log.guard("CarUxRestrictionsManager.unregisterListener") { ux?.unregisterListener() }
        log.guard("CarPropertyManager.unregisterCallback") { manager?.unregisterCallback(callback) }
        log.guard("Car.disconnect") { car?.disconnect() }
        ux = null
        manager = null
        car = null
    }

    /** 점검 탭의 가상 기어 버튼 (일반 에뮬레이터에서 흐름만 볼 때) */
    fun simulate(gear: Gear) {
        _gear.value = gear
        log.ok("가상 기어", gear.name)
    }

    private fun map(raw: Int): Gear = when (raw) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE -> Gear.DRIVE
        else -> if (raw >= VehicleGear.GEAR_FIRST) Gear.DRIVE else Gear.UNKNOWN
    }
}
