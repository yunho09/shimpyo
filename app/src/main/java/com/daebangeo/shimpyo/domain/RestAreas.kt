package com.daebangeo.shimpyo.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 시연용 휴게소·졸음쉼터 목록.
 *
 * ⚠️ 좌표는 시연용 근사값이다. 실제 서비스 전에 한국도로공사 휴게소 공공데이터
 * (공공데이터포털 "한국도로공사_휴게소 정보")로 교체하고, 상·하행 구분도 함께 넣어야 한다.
 */
object RestAreas {
    val seed: List<RestArea> = listOf(
        RestArea("죽전휴게소", 37.3248, 127.1047, "경부고속도로"),
        RestArea("기흥휴게소", 37.2330, 127.1110, "경부고속도로"),
        RestArea("안성휴게소", 37.0060, 127.2130, "경부고속도로"),
        RestArea("망향휴게소", 36.8670, 127.1960, "경부고속도로"),
        RestArea("천안삼거리휴게소", 36.8230, 127.1750, "경부고속도로"),
        RestArea("옥산휴게소", 36.6770, 127.3500, "경부고속도로"),
        RestArea("죽암휴게소", 36.4800, 127.4210, "경부고속도로"),
        RestArea("신탄진휴게소", 36.4520, 127.4400, "경부고속도로"),
        RestArea("서울만남의광장휴게소", 37.4600, 127.0420, "경부고속도로"),
        RestArea("시흥하늘휴게소", 37.3870, 126.8320, "수도권제1순환고속도로"),
    )

    data class Nearest(val restArea: RestArea, val distanceKm: Double, val ahead: Boolean)

    /** 진행 방향 ±60° 안에 있으면 "앞" */
    private const val AHEAD_HALF_ANGLE = 60.0
    /** 이보다 가까우면 이미 지나쳤거나 들어가기 어렵다고 본다 */
    private const val MIN_AHEAD_KM = 1.0

    fun nearest(latitude: Double, longitude: Double, candidates: List<RestArea> = seed): Nearest? =
        candidates
            .map { Nearest(it, distanceKm(latitude, longitude, it.latitude, it.longitude), ahead = false) }
            .minByOrNull { it.distanceKm }

    /**
     * 진행 방향 앞쪽의 가장 가까운 휴게소. 앞쪽에 없거나 방향을 모르면 가장 가까운 곳.
     * headingDeg: 북쪽 0°, 시계 방향. null이면 방향 모름.
     */
    fun nearestAhead(
        latitude: Double,
        longitude: Double,
        headingDeg: Double?,
        candidates: List<RestArea> = seed,
    ): Nearest? {
        if (headingDeg == null) return nearest(latitude, longitude, candidates)
        val ahead = candidates
            .map { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .filter { (area, km) ->
                km >= MIN_AHEAD_KM &&
                    angleDiff(bearingDeg(latitude, longitude, area.latitude, area.longitude), headingDeg) <= AHEAD_HALF_ANGLE
            }
            .minByOrNull { it.second }
        return ahead?.let { Nearest(it.first, it.second, ahead = true) } ?: nearest(latitude, longitude, candidates)
    }

    /** 하버사인 거리(km) */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** 1 → 2 방위각 (북 0°, 시계 방향, 0..360) */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
