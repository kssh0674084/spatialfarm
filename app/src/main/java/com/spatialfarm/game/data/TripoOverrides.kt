package com.spatialfarm.game.data

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * Tripo 生成资产的覆盖配置（约定优于配置）。
 *
 * 放置规则：把 Tripo 导出的 GLB 命名为 `<itemId>.glb` 放进
 * `app/src/main/assets/models/tripo/`，游戏加载时自动优先使用（失败回退内置模型）。
 *
 * 可选 `assets/models/tripo/manifest.json` 用于逐件修正比例与朝向：
 * ```json
 * { "wang_lawang": { "scale": 1.0, "yaw": 180 } }
 * ```
 */
object TripoOverrides {
    private val overrides = mutableSetOf<String>()
    private var manifest: JSONObject? = null
    private var loaded = false

    fun init(assets: AssetManager) {
        if (loaded) return
        loaded = true
        manifest =
            runCatching {
                assets.open("models/tripo/manifest.json").bufferedReader().use {
                    JSONObject(it.readText())
                }
            }.getOrNull()
        runCatching {
            assets.list("models/tripo")?.forEach { f ->
                if (f.endsWith(".glb")) overrides.add(f.removeSuffix(".glb"))
            }
        }
    }

    /** 覆盖模型 URI；无覆盖返回 null */
    fun uriFor(itemId: String): String? =
        if (overrides.contains(itemId)) "asset://models/tripo/$itemId.glb" else null

    fun scaleFor(itemId: String): Float =
        manifest?.optJSONObject(itemId)?.optDouble("scale", 1.0)?.toFloat() ?: 1f

    fun yawFor(itemId: String): Float =
        manifest?.optJSONObject(itemId)?.optDouble("yaw", 0.0)?.toFloat() ?: 0f
}
