package com.spatialfarm.game.scene.neighbor

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.scene.plot.FarmPlotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * 邻居老王：全村唯一不种地的人。
 *
 * 作息循环：在家发呆（40-70s）→ 溜达到田边 → 偷一块成熟作物（稻草人可拦截）
 * → 溜达回家 → 循环。
 *
 * 玩家反制：趁他不在家时点他的菜地偷菜；他在家时偷会被罚款。
 */
class LaoWangEntity(
    private val plots: List<FarmPlotEntity>,
    private val homePoint: Vector3 = Vector3(-5.2f, 0f, -5.6f),
    private val farmPoint: Vector3 = Vector3(-3.4f, 0f, -3.7f),
) : Entity() {

    /** 老王当前状态（玩家偷菜判定用） */
    enum class State { AT_HOME, WALKING, AT_FARM, STEALING }

    var state: State = State.AT_HOME
        private set

    private val scope = CoroutineScope(Dispatchers.Main)
    private var body: Entity? = null

    /** 他家门口的菜地（玩家可偷的目标点） */
    lateinit var stealSpot: StealSpotEntity
        private set

    init {
        setUp()
    }

    private fun setUp() {
        setName("lao_wang")
        buildBody()
        components[TransformComponent::class.java]?.setPosition(homePoint)

        stealSpot = StealSpotEntity(this)
        stealSpot.components[TransformComponent::class.java]
            ?.setPosition(Vector3(homePoint.x - 0.9f, 0f, homePoint.z + 0.5f))
        addChild(stealSpot)

        scope.launch { lifeLoop() }
    }

    // ---------------------------------------------------------------- 身体（几何体拼装小人）

    private fun buildBody() {
        val root = Entity()
        root.setName("lao_wang_body")

        // 身体（棕色夹克）
        val bodyModel = ModelEntity(
            MeshResource.createCapsule(0.62f, 0.20f),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.55f, 0.42f, 0.30f, 1f)) }
        )
        bodyModel.setName("body")
        bodyModel.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.45f, 0f))
        root.addChild(bodyModel)

        // 头（肤色球）
        val head = ModelEntity(
            MeshResource.createSphere(com.pico.spatial.core.ecs.resource.SphereOption(0.15f)),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.98f, 0.85f, 0.70f, 1f)) }
        )
        head.setName("head")
        head.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.92f, 0f))
        root.addChild(head)

        // 草帽（帽檐 + 帽顶）
        val brim = ModelEntity(
            MeshResource.createCylinder(0.045f, 0.26f),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.93f, 0.82f, 0.55f, 1f)) }
        )
        brim.setName("hat_brim")
        brim.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 1.04f, 0f))
        root.addChild(brim)

        val crown = ModelEntity(
            MeshResource.createCylinder(0.10f, 0.13f),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.88f, 0.75f, 0.48f, 1f)) }
        )
        crown.setName("hat_crown")
        crown.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 1.10f, 0f))
        root.addChild(crown)

        // 点击身体也能和他互动了解状态（小人本身不罚款，无交互需求）
        body = root
        addChild(root)
    }

    // ---------------------------------------------------------------- 作息循环

    private suspend fun lifeLoop() {
        while (valid) {
            // 在家发呆
            state = State.AT_HOME
            delay(Random.nextLong(40_000, 70_000))

            // 溜达到田边
            state = State.WALKING
            walk(homePoint, farmPoint, 3200)

            // 偷菜！
            state = State.STEALING
            delay(1200)
            trySteal()
            state = State.AT_FARM
            delay(1500)

            // 回家
            state = State.WALKING
            walk(farmPoint, homePoint, 3200)
        }
    }

    private suspend fun walk(from: Vector3, to: Vector3, durationMs: Long) {
        val steps = (durationMs / 50).toInt().coerceAtLeast(1)
        val bobAmp = 0.02f
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            x = from.x + (to.x - from.x) * t
            z = from.z + (to.z - from.z) * t
            y = abs(kotlin.math.sin(t * Math.PI * 8).toFloat()) * bobAmp
            components[TransformComponent::class.java]?.setPosition(Vector3(x, y, z))
            delay(50)
        }
        components[TransformComponent::class.java]?.setPosition(Vector3(to.x, 0f, to.z))
    }

    // 简化位置操作
    private var x = homePoint.x
    private var y = 0f
    private var z = homePoint.z

    private fun trySteal() {
        // 新手保护：玩家累计收获不足 3 次前，老王不会下手（避免首次种菜就被偷光的挫败感）
        if (GameState.harvestLog.values.sum() < 3) {
            GameState.postHint("老王来田边转了一圈，看你刚学种菜，没好意思下手 😏")
            return
        }
        val mature = plots.filter { it.cropState() == 5 }
        if (mature.isEmpty()) return
        val target = mature.random()
        val successChance = if (GameState.hasScarecrow) 0.35f else 0.95f
        if (Random.nextFloat() > successChance) {
            GameState.postHint("老王在田边转了一圈，被稻草人吓跑了 😤")
            return
        }
        val stolen = target.thiefHarvest()
        if (stolen != null) {
            GameState.registerTheft()
            GameState.postHint("😱 老王偷走了你的 ${stolen.emoji}${stolen.name}！买稻草人防他！")
        }
    }
}

/**
 * 老王家的菜地（玩家可偷的目标）。
 * 趁老王不在家（WALKING/AT_FARM/STEALING）时点按 → 偷到随机作物；
 * 老王在家时点按 → 被逮到罚款 ¥200。
 */
class StealSpotEntity(private val owner: LaoWangEntity) : Entity() {

    init {
        setName("laowang_steal_spot")
        val crop = ModelEntity(
            MeshResource.createBox(com.pico.spatial.core.ecs.resource.BoxOption(Vector3(0.5f, 0.4f, 0.5f)).setCornerRadius(0.1f).setName("steal_crate")),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.86f, 0.62f, 0.28f, 1f)) }
        )
        crop.setName("steal_crate_body")
        crop.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.2f, 0f))
        addChild(crop)

        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(0.6f, 0.6f, 0.6f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
    }

    fun onTap() {
        if (owner.state == LaoWangEntity.State.AT_HOME) {
            if (GameState.fined(200)) {
                GameState.postHint("💢 被老王逮个正着！罚了 ¥200")
            } else {
                GameState.postHint("💢 被老王逮个正着！（钱包空了，罚单赊账）")
            }
            return
        }
        val loot = listOf("pumpkin", "tomato").random()
        GameState.addProduce(loot, 1)
        GameState.registerPlayerSteal()
        val def = com.spatialfarm.game.data.plantById(loot)
        GameState.postHint("🤫 趁老王不在家，偷了一颗 ${def?.emoji ?: ""}${def?.name ?: loot}！")
    }
}
