package com.daebangeo.shimpyo.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApiLogEntry(
    val timeMs: Long,
    val api: String,
    val ok: Boolean,
    val detail: String,
)

/**
 * SDK 호출 결과를 모아 "SDK 확인" 탭에 보여준다.
 * 에뮬레이터에서 어떤 API가 실제로 되는지 눈으로 확인하는 용도.
 */
class ApiLog {
    private val _entries = MutableStateFlow<List<ApiLogEntry>>(emptyList())
    val entries: StateFlow<List<ApiLogEntry>> = _entries.asStateFlow()

    fun ok(api: String, detail: String = "") = add(api, true, detail)

    fun fail(api: String, t: Throwable) = add(api, false, "${t.javaClass.simpleName}: ${t.message ?: ""}")

    fun failMsg(api: String, detail: String) = add(api, false, detail)

    fun clear() = _entries.update { emptyList() }

    private fun add(api: String, ok: Boolean, detail: String) {
        if (ok) Log.d(TAG, "OK $api $detail") else Log.w(TAG, "FAIL $api $detail")
        val entry = ApiLogEntry(System.currentTimeMillis(), api, ok, detail)
        _entries.update { (listOf(entry) + it).take(MAX) }
    }

    companion object {
        const val TAG = "Shimpyo"
        private const val MAX = 200
    }
}

/** SDK가 동기 예외를 던져도 앱이 죽지 않게 감싼다. */
inline fun ApiLog.guard(api: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        fail(api, t)
    }
}
