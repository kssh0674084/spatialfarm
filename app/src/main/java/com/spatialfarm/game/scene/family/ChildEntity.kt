package com.spatialfarm.game.scene.family

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationTransitionMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.TripoOverrides
import kotlinx.coroutines.launch

/**
 * 家里的孩子（可点按帮忙）：
 * - 小花（女儿·粉色）：帮全田浇水一次（品质 +10/田）——又要可爱又贴心
 * - 小勇（儿子·蓝色）：帮收所有成熟作物入篮
 * 均有 60 秒冷却（孩子也要休息）。
 */
enum class ChildKind { DAUGHTER, SON }

class ChildEntity(
    private val kind: ChildKind,
    private val plotsProvider: () -> List<com.spatialfarm.game.scene.plot.FarmPlotEntity>,
) : Entity() {

    private var lastHelpAt = 0L

    init {
        setName(if (kind == ChildKind.DAUGHTER) "child_daughter" else "child_son")
        buildBody()
        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(0.45f, 1.0f, 0.45f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
    }

    private fun buildBody() {
        val itemId = if (kind == ChildKind.DAUGHTER) "child_flower" else "child_yong"
        val defaultPath =
            if (kind == ChildKind.DAUGHTER) "models/characters/Child_Girl_Casual.glb"
            else "models/characters/Casual2.glb"
        val modelPath = TripoOverrides.uriFor(itemId)?.removePrefix("asset://") ?: defaultPath
        val baseScale = if (kind == ChildKind.DAUGHTER) 0.60f else 0.63f
        val scale = baseScale * TripoOverrides.scaleFor(itemId)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val model = Entity.loadSuspend("asset://$modelPath")
            if (!valid) {
                model.destroy(true)
                return@launch
            }
            model.components[TransformComponent::class.java]?.apply {
                setPosition(
                    Vector3(0f, TripoOverrides.offsetYFor(itemId) * scale, 0f)
                )
                setScaleVector(Vector3(scale, scale, scale))
                setEulerAngles(
                    com.pico.spatial.core.math.EulerAngles(
                        0f, 180f + TripoOverrides.yawFor(itemId), 0f
                    )
                )
            }
            addChild(model)
            // 待机动画（Tripo 自动绑骨 + preset:idle）
            runCatching {
                val clips = model.getAnimationResources()
                android.util.Log.i("SFARM", "child $itemId clips=" + clips.joinToString { it.getName() })
                val idle = clips
                    .firstOrNull { it.getName().contains("idle", true) }
                    ?.repeat(-1)
                if (idle != null) {
                    model.playAnimation(
                        idle,
                        AnimationPlayConfig(0.2f, AnimationTransitionMode.CROSSFADE, 0, 1f),
                    )
                }
            }.onFailure { android.util.Log.i("SFARM", "child anim fail: ${it.message}") }
        }
    }

    /** 用户点按孩子：帮忙（60 秒冷却）。 */
    fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastHelpAt < COOLDOWN_MS) {
            GameState.postHint("${name()}还在休息，等一会儿再让她帮忙 ⏳")
            return
        }
        val plots = plotsProvider()
        when (kind) {
            ChildKind.DAUGHTER -> helpWater(plots)
            ChildKind.SON -> helpHarvest(plots)
        }
        lastHelpAt = now
    }

    private fun helpWater(plots: List<com.spatialfarm.game.scene.plot.FarmPlotEntity>) {
        val growing = plots.filter { it.cropState() in 1..4 }
        if (growing.isEmpty()) {
            GameState.postHint("小花：田里没有正在长的菜啦~ 🌸")
            return
        }
        growing.forEach { it.helpWaterByChild() }
        GameState.postHint("🌸 小花帮你把 ${growing.size} 块田都浇了一遍水！")
    }

    private fun helpHarvest(plots: List<com.spatialfarm.game.scene.plot.FarmPlotEntity>) {
        val mature = plots.filter { it.cropState() == 5 }
        if (mature.isEmpty()) {
            GameState.postHint("小勇：没有熟了的菜，我再去玩会儿~ 🛴")
            return
        }
        val names = mature.mapNotNull { it.helpHarvestByChild()?.name }.joinToString("、")
        GameState.postHint("🛴 小勇帮你收了：$names！都进篮子啦")
    }

    private fun name() = if (kind == ChildKind.DAUGHTER) "小花" else "小勇"

    private companion object {
        const val COOLDOWN_MS = 60_000L
    }
}
