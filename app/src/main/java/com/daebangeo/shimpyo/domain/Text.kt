package com.daebangeo.shimpyo.domain

import java.util.Calendar
import java.util.TimeZone

/** 120분 → "2시간", 20분 → "20분", 20초 → "20초" (시연 모드에서도 자연스럽게 읽히도록) */
fun label(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    if (totalSec < 60) return "${totalSec}초"
    val totalMin = totalSec / 60
    if (totalMin < 60) return "${totalMin}분"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (m == 0L) "${h}시간" else "${h}시간 ${m}분"
}

/** 목적격 조사: 받침 있으면 "을", 없으면 "를" (안성휴게소를, 죽전휴게소를, 시흥하늘휴게소를) */
fun objectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "를"
    if (last !in '가'..'힣') return "을(를)"
    return if ((last - '가') % 28 != 0) "을" else "를"
}

/** 방향 조사: 받침 없거나 ㄹ 받침이면 "로", 아니면 "으로" (안성휴게소로, 서울만남의광장으로) */
fun directionParticle(word: String): String {
    val last = word.lastOrNull() ?: return "로"
    if (last !in '가'..'힣') return "(으)로"
    val jong = (last - '가') % 28
    return if (jong == 0 || jong == 8) "로" else "으로"
}

/** 지금부터 remainSec 뒤의 시각을 "17:40" 형태로 */
fun clockAfter(nowMs: Long, remainSec: Int, zone: TimeZone = TimeZone.getDefault()): String {
    val cal = Calendar.getInstance(zone)
    cal.timeInMillis = nowMs + remainSec * 1000L
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    return "%02d:%02d".format(h, m)
}

/** 음성 인식 결과를 대답으로 바꾼다. 못 알아들으면 null → 화면 버튼으로 대신. */
object VoiceParser {
    private val extend = listOf("더잘", "더자", "5분만", "오분만", "조금만더", "좀더", "더잘래")
    private val nap = listOf("잘게", "잘께", "잘래", "잘거", "잠깐잘", "쪽잠", "자고갈", "잘까", "재워")
    private val no = listOf("아니", "싫어", "괜찮아", "됐어", "나중에", "안쉬", "그냥가", "그냥갈", "필요없")
    private val yes = listOf("응", "네", "예", "좋아", "그래", "쉬어갈", "쉬자", "콜", "오케이", "ok", "부탁", "가자")

    fun parse(raw: String): Answer? {
        val t = raw.lowercase().filter { it.isLetterOrDigit() }
        if (t.isEmpty()) return null
        return when {
            extend.any { it in t } -> Answer.EXTEND
            nap.any { it in t } -> Answer.NAP
            no.any { it in t } -> Answer.NO
            yes.any { it in t } -> Answer.YES
            else -> null
        }
    }
}

/** 기록 탭 숫자들 */
object RecordStats {
    private const val DAY = 24 * 60 * 60 * 1000L

    /** 최근 7일 vs 그 전 7일 쉰 횟수 */
    fun weekCounts(records: List<NapRecord>, nowMs: Long): Pair<Int, Int> {
        val thisWeek = records.count { it.startedAtMs > nowMs - 7 * DAY && it.startedAtMs <= nowMs }
        val lastWeek = records.count { it.startedAtMs > nowMs - 14 * DAY && it.startedAtMs <= nowMs - 7 * DAY }
        return thisWeek to lastWeek
    }

    /** "지난 7일보다 1번 많아요" */
    fun weekChange(thisWeek: Int, lastWeek: Int): String = when {
        thisWeek > lastWeek -> "지난 7일보다 ${thisWeek - lastWeek}번 많아요"
        thisWeek < lastWeek -> "지난 7일보다 ${lastWeek - thisWeek}번 적어요"
        else -> "지난 7일과 같아요"
    }

    /** 최근 5번, 쉬기 전 운전 시간의 중앙값 */
    fun medianDrivenMs(records: List<NapRecord>): Long? {
        val v = records.sortedBy { it.startedAtMs }.takeLast(5).map { it.drivenBeforeMs }.filter { it > 0 }.sorted()
        if (v.isEmpty()) return null
        return if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
    }
}

/** 운전·휴식 기록으로 다음 휴식 제안 시점을 정한다. */
object RestAdvisor {
    private const val MIN = 60_000L

    /**
     * 최근 기록 5개에서 "쉬기 전에 몇 분 운전했는지"의 중앙값을 쓰고, 1~2시간 사이로 자른다.
     * 기록이 없으면 2시간.
     */
    fun recommendedRestAfterMs(
        records: List<NapRecord>,
        defaultMs: Long = 120 * MIN,
        minMs: Long = 60 * MIN,
        maxMs: Long = 120 * MIN,
    ): Long {
        val recent = records.sortedBy { it.startedAtMs }.takeLast(5).map { it.drivenBeforeMs }.filter { it > 0 }
        if (recent.isEmpty()) return defaultMs
        val sorted = recent.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        return median.coerceIn(minMs, maxMs)
    }
}
