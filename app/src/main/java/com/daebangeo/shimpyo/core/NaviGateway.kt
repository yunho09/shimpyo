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
import kotlinx.coroutines.delay
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

    /** 마지막으로 안내한 휴게소 이름과, 그게 경유지였는지(아니면 목적지) */
    private var restName: String? = null
    private var restIsWaypoint = false

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
            // 실차: 경유지에 도착하면 내비가 경유지를 지나간 것으로 처리한다
            if (restIsWaypoint) restIsWaypoint = false
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
     * 쪽잠 뒤 출발 브리핑용 최종 목적지.
     * 내비가 아직 쉬었던 휴게소를 "다음 목적지"로 알려 주면(에뮬레이터처럼 차가 실제로 경유지에 도착하지 않은 경우):
     * - 휴게소가 경유지였으면 그 경유지를 지우고 다시 받아 최종 목적지(예: 부산역)를 쓴다
     * - 휴게소가 목적지였으면 남은 목적지가 없으므로 null (→ "안전운전하세요")
     */
    suspend fun destinationAfterRest(timeoutMs: Long): DestinationInfo? {
        val d = freshDestination(timeoutMs)
        val rest = restName
        if (d == null || rest == null || d.name != rest) return d
        if (!restIsWaypoint) return null
        log.guard("NaviHelper.removeWaypoint") {
            navi.removeWaypoint(WaypointIndex.FIRST)
            log.ok("NaviHelper.removeWaypoint", "$rest (쉬고 출발 → 지나간 경유지 정리)")
        }
        restIsWaypoint = false
        delay(800) // 경로 다시 계산할 시간
        return freshDestination(timeoutMs)?.takeIf { it.name != rest }
    }

    /**
     * 휴게소를 경유지로 넣는다.
     * 목적지가 없으면 경유지를 넣을 수 없으므로 휴게소를 목적지로 안내한다.
     */
    fun goVia(area: RestArea) {
        if (!connected) return log.failMsg("NaviHelper.addWaypoint", "아직 연결 안 됨")
        // 목적지가 없어도 DestinationInfo는 빈 값(이름 "", 남은 거리 0)으로 온다 → 그때는 경로 요청
        val dest = _destination.value
        if (dest != null && (dest.remainDistance > 0 || dest.name.isNotBlank())) {
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
                restName = area.name
                restIsWaypoint = true
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
                restName = area.name
                restIsWaypoint = false
            }
        }
    }
}
