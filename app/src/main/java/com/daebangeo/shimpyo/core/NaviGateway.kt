package com.daebangeo.shimpyo.core

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.daebangeo.shimpyo.domain.RestArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos NaviHelper SDK 래퍼 (문서 기준 v3.0.5).
 * 요청 결과는 NaviHelperEventListener 콜백으로 온다 → StateFlow로 바꿔서 쓴다.
 */
class NaviGateway(context: Context, private val log: ApiLog) {

    private val navi = NaviHelper(context)
    private var connected = false

    private val _location = MutableStateFlow<CurrentLocationInfo?>(null)
    val location: StateFlow<CurrentLocationInfo?> = _location.asStateFlow()

    private val _destination = MutableStateFlow<DestinationInfo?>(null)
    val destination: StateFlow<DestinationInfo?> = _destination.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _location.value = currentLocationInfo
            log.ok("Navi.onCurrentLocationInfo", "${currentLocationInfo.latitude}, ${currentLocationInfo.longitude}")
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            _destination.value = destinationInfo
            log.ok("Navi.onDestinationInfo", "${destinationInfo.name} 남은 ${destinationInfo.remainTime}")
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            log.ok("Navi.onWayPointArrived", "$waypointArrivedInfo")
        }

        override fun onError(errorCode: NaviErrorCode) {
            log.failMsg("Navi.onError", "$errorCode")
        }
    }

    fun start() {
        log.guard("NaviHelper.initialize") {
            navi.initialize(
                onConnected = {
                    connected = true
                    log.ok("NaviHelper.initialize", "connected")
                    log.guard("NaviHelper.addListener") { navi.addListener(listener) }
                    requestLocation()
                    requestDestination()
                },
                onFailed = { t -> log.fail("NaviHelper.initialize", t) },
            )
        }
    }

    fun stop() {
        log.guard("NaviHelper.removeListener") { navi.removeListener(listener) }
        log.guard("NaviHelper.release") { navi.release() }
    }

    fun requestLocation() {
        if (!connected) return log.failMsg("NaviHelper.getCurrentLocationInfo", "아직 연결 안 됨")
        log.guard("NaviHelper.getCurrentLocationInfo") { navi.getCurrentLocationInfo() }
    }

    fun requestDestination() {
        if (!connected) return log.failMsg("NaviHelper.getDestinationInfo", "아직 연결 안 됨")
        log.guard("NaviHelper.getDestinationInfo") { navi.getDestinationInfo() }
    }

    /** 현재 위치를 새로 받아 온다. 시간 안에 안 오면 마지막 값(없으면 null). */
    suspend fun freshLocation(timeoutMs: Long): CurrentLocationInfo? {
        requestLocation()
        return withTimeoutOrNull(timeoutMs) { _location.drop(1).filterNotNull().first() } ?: _location.value
    }

    /** 목적지 정보(남은 시간 포함)를 새로 받아 온다. */
    suspend fun freshDestination(timeoutMs: Long): DestinationInfo? {
        requestDestination()
        return withTimeoutOrNull(timeoutMs) { _destination.drop(1).filterNotNull().first() } ?: _destination.value
    }

    /**
     * 휴게소를 경유지로 넣는다.
     * 목적지가 없으면 경유지를 넣을 수 없으므로 휴게소를 목적지로 안내한다.
     */
    fun goVia(area: RestArea) {
        if (!connected) return log.failMsg("NaviHelper.addWaypoint", "아직 연결 안 됨")
        if (_destination.value != null) {
            log.guard("NaviHelper.addWaypoint") {
                navi.addWaypoint(
                    RequestWaypointInfo(
                        latitude = area.latitude,
                        longitude = area.longitude,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        poiName = area.name,
                        poiSubId = "0",
                        address = area.road,
                    ),
                )
                log.ok("NaviHelper.addWaypoint", area.name)
            }
        } else {
            log.guard("NaviHelper.requestRoute") {
                navi.requestRoute(
                    RouteInfo(
                        latitude = area.latitude,
                        longitude = area.longitude,
                        displayLongitude = null,
                        displayLatitude = null,
                        poiName = area.name,
                        poiId = "",
                        poiSubId = "0",
                        address = area.road,
                    ),
                )
                log.ok("NaviHelper.requestRoute", "${area.name} (목적지 없음 → 휴게소를 목적지로)")
            }
        }
    }
}
