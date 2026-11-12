package com.spatialfarm.game.data

import android.content.Context
import org.json.JSONObject

/**
 * 存档：SharedPreferences + JSON（轻量、无依赖）。
 * 自动保存由 [com.spatialfarm.game.platform.SpatialApplication] 的周期任务触发。
 */
object SaveStore {
    private const val PREFS = "spatialfarm_save"
    private const val KEY = "save_v1"

    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun write(json: JSONObject) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.toString())
            .apply()
    }

    fun read(): JSONObject? {
        val ctx = appContext ?: return null
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
