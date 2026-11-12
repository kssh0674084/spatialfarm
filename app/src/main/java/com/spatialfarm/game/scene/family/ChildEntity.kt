package com.spatialfarm.game.scene.family

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState

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
        val cloth = if (kind == ChildKind.DAUGHTER) {
            Color4(0.95f, 0.70f, 0.75f, 1f) // 粉色
        } else {
            Color4(0.55f, 0.72f, 0.92f, 1f) // 蓝色
        }
        // 小身体
        val body = ModelEntity(
            MeshResource.createCapsule(0.42f, 0.15f),
            UnlitMaterial.create().apply { setBaseColor(cloth) }
        )
        body.setName("child_body")
        body.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.31f, 0f))
        addChild(body)

        // 头
        val head = ModelEntity(
            MeshResource.createSphere(SphereOption(0.12f)),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.99f, 0.87f, 0.74f, 1f)) }
        )
        head.setName("child_head")
        head.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.66f, 0f))
        addChild(head)

        // 头发（女儿双丸子头 / 儿子小平头）
        val hairMat = UnlitMaterial.create().apply { setBaseColor(Color4(0.35f, 0.25f, 0.18f, 1f)) }
        if (kind == ChildKind.DAUGHTER) {
            for (x in listOf(-0.1f, 0.1f)) {
                val bun = ModelEntity(MeshResource.createSphere(SphereOption(0.055f)), hairMat)
                bun.setName("bun_$x")
                bun.components[TransformComponent::class.java]!!
                    .setPosition(Vector3(x, 0.76f, 0f))
                addChild(bun)
            }
        } else {
            val hair = ModelEntity(
                MeshResource.createCylinder(0.06f, 0.115f),
                hairMat
            )
            hair.setName("flat_hair")
            hair.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.75f, 0f))
            addChild(hair)
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
