package com.daebangeo.shimpyo.core

import android.content.Context
import com.daebangeo.shimpyo.domain.NapRecord
import com.daebangeo.shimpyo.domain.Powertrain
import org.json.JSONArray
import org.json.JSONObject

/** 기록·설정을 기기 안에만 저장 (서버 전송 없음). */
class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("shimpyo", Context.MODE_PRIVATE)

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, true)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    var powertrain: Powertrain
        get() = runCatching { Powertrain.valueOf(prefs.getString(KEY_POWERTRAIN, null) ?: "") }.getOrDefault(Powertrain.HYBRID)
        set(value) = prefs.edit().putString(KEY_POWERTRAIN, value.name).apply()

    fun records(): List<NapRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NapRecord(
                    startedAtMs = o.getLong("startedAt"),
                    napMs = o.getLong("napMs"),
                    drivenBeforeMs = o.getLong("drivenBeforeMs"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addRecord(record: NapRecord): List<NapRecord> {
        val all = (records() + record).takeLast(MAX_RECORDS)
        val arr = JSONArray()
        all.forEach {
            arr.put(
                JSONObject()
                    .put("startedAt", it.startedAtMs)
                    .put("napMs", it.napMs)
                    .put("drivenBeforeMs", it.drivenBeforeMs),
            )
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
        return all
    }

    fun clearRecords() = prefs.edit().remove(KEY_RECORDS).apply()

    companion object {
        private const val KEY_DEMO = "demo"
        private const val KEY_POWERTRAIN = "powertrain"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 50
    }
}
