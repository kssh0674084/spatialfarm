package com.spatialfarm.game.scene.plot

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BoxOption
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.PlantDef
import com.spatialfarm.game.scene.audio.Sfx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一块田垄：种植状态机。
 *
 * 空田 → 点按播种（消耗选中种子）→ 4 阶段生长（点按浇水提速+加品质）
 * → 成熟（_Crop 模型）→ 点按收获入篮 → 回到空田。
 *
 * 生长速度：1 游戏日 = [GROW_SECONDS_PER_DAY] 秒（演示加速版）。
 */
class FarmPlotEntity(
    private val index: Int,
    locked: Boolean = false,
) : Entity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var plant: PlantDef? = null
    private var stage = 0                 // 0..3 生长中，>= STAGES 表示成熟
    private var stageProgress = 0f        // 当前阶段已过秒数
    private var quality = 0               // 照料品质 0..100（M2-4 卖价乘数）
    private var plantRoot: Entity? = null
    private var generation = 0            // 防止异步加载竞态
    private var locked = locked           // 未购买的荒地
    private var soilChild: ModelEntity? = null

    init {
        setUp()
    }

    private fun setUp() {
        setName("plot_$index")

        // 土壤（荒地为草绿色，购入后变棕色田垄）
        val soil = ModelEntity(soilMesh(), if (locked) grassMaterial() else soilMaterial())
        soil.setName("plot_${index}_soil")
        soil.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, PLOT_T / 2f, 0f))
        soilChild = soil
        addChild(soil)

        // 交互：碰撞盒 + 可交互 + 悬停高亮
        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(PLOT_W, 0.5f, PLOT_D))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
        Sfx.attachPointSource(this, volume = 0.7f)

        // 生长心跳
        scope.launch {
            while (valid) {
                delay(TICK_MS)
                tick(TICK_MS / 1000f)
            }
        }
    }

    /** 从存档恢复（VillageScene 创建田垄后调用）。 */
    fun restore(snapshot: com.spatialfarm.game.data.PlotSnapshot) {
        val def = com.spatialfarm.game.data.plantById(snapshot.plantId) ?: return
        locked = false
        plant = def
        stage = snapshot.stage
        stageProgress = snapshot.stageProgress
        quality = snapshot.quality
        generation++
        refreshPlantModel()
    }

    /** 记录快照到全局存档 */
    private fun snapshot() {
        GameState.snapshotPlot(index, plant?.id, stage, stageProgress, quality)
    }

    /** 购买后开垦：荒地变良田。 */
    fun unlock() {
        if (!locked) return
        locked = false
        soilChild?.destroy(true)
        val soil = ModelEntity(soilMesh(), soilMaterial())
        soil.setName("plot_${index}_soil")
        soil.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, PLOT_T / 2f, 0f))
        soilChild = soil
        addChild(soil)
    }

    /** 用户点按田垄。 */
    fun onTap() {
        if (locked) {
            GameState.postHint("这块地还是荒地，先点「🪴 扩田」购买")
            return
        }
        val p = plant
        if (p == null) {
            plantFromSelection()
            return
        }
        if (stage >= STAGES) {
            harvest(p)
            return
        }
        water(p)
    }

    /** 被老王偷菜：成熟作物被拿走（不入篮），返回被偷的作物；非成熟返回 null。 */
    fun thiefHarvest(): PlantDef? {
        val p = plant ?: return null
        if (stage < STAGES) return null
        plant = null
        plantRoot?.destroy(true)
        plantRoot = null
        stage = 0
        stageProgress = 0f
        quality = 0
        generation++
        snapshot()
        return p
    }

    /** 0=空田，1..4=生长中，5=成熟（HUD 快捷操作判断用） */
    fun cropState(): Int =
        when {
            plant == null -> 0
            stage >= STAGES -> 5
            else -> stage + 1
        }

    // ---------------------------------------------------------------- 动作

    private fun plantFromSelection() {
        val def = GameState.selectedPlant()
        if (def == null) {
            GameState.postHint("先去种子商店买种子吧 🏪")
            return
        }
        if (!GameState.consumeSeed(def.id)) {
            GameState.postHint("${def.name}种子用完了，去商店补货 🏪")
            return
        }
        plant = def
        stage = 0
        stageProgress = 0f
        quality = 0
        generation++
        refreshPlantModel()
        snapshot()
        GameState.postHint("种下了 ${def.emoji}${def.name}，点它浇水 💧")
    }

    private fun water(p: PlantDef) {
        val sprinkler = GameState.hasSprinkler
        quality = (quality + if (sprinkler) 30 else 20).coerceAtMost(100)
        var boost = if (sprinkler) 0.20f else 0.12f
        val fertilized = GameState.consumeFertilizer()
        if (fertilized) boost += 0.25f
        stageProgress += stageDuration(p) * boost
        val fertTag = if (fertilized) " 🌱施肥" else ""
        snapshot()
        Sfx.playWater(this)
        GameState.postHint("浇水 💧 ${p.emoji}${p.name} 品质 $quality$fertTag")
    }

    private fun harvest(p: PlantDef) {
        val bonus = if (quality >= 60) 1 else 0
        Sfx.playHarvest(this)
        GameState.addProduce(p.id, 1 + bonus)
        GameState.postHint("收获 ${p.emoji}${p.name} ×${1 + bonus}！去收购站卖钱")
        plant = null
        plantRoot?.destroy(true)
        plantRoot = null
        stage = 0
        stageProgress = 0f
        quality = 0
        generation++
        snapshot()
    }

    // ---------------------------------------------------------------- 生长

    private fun tick(dt: Float) {
        val p = plant ?: return
        if (stage >= STAGES) return
        stageProgress += dt
        if (stageProgress >= stageDuration(p)) {
            stageProgress = 0f
            stage++
            refreshPlantModel()
            snapshot()
        }
    }

    private fun stageDuration(p: PlantDef): Float =
        p.growDays * GROW_SECONDS_PER_DAY / STAGES

    private fun refreshPlantModel() {
        val p = plant ?: return
        val myGen = ++generation
        val mature = stage >= STAGES
        val uri =
            if (mature) "models/crops/${p.assetBase}_Crop.glb"
            else "models/crops/${p.assetBase}_${stage + 1}.glb"

        scope.launch {
            val model = Entity.loadSuspend("asset://$uri")
            if (!valid || myGen != generation) {
                model.destroy(true)
                return@launch
            }
            plantRoot?.destroy(true)
            plantRoot = model
            val s = cropScale(p)
            model.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, PLOT_T, 0f))
                setScaleVector(Vector3(s, s, s))
            }
            addChild(model)
            if (mature) {
                GameState.postHint("${p.emoji}${p.name}成熟了！点它收获 🧺")
            }
        }
    }

    // ---------------------------------------------------------------- 资源

    private companion object {
        const val PLOT_W = 1.3f
        const val PLOT_T = 0.22f
        const val PLOT_D = 0.9f
        const val STAGES = 4
        const val GROW_SECONDS_PER_DAY = 60f   // 1 游戏日 = 60 秒（演示）
        const val TICK_MS = 200L
        const val WATER_BOOST = 0.12f          // 每次浇水推进 12% 当前阶段

        var sharedSoilMesh: MeshResource? = null
        var sharedSoilMat: UnlitMaterial? = null
        var sharedGrassMat: UnlitMaterial? = null

        fun soilMesh(): MeshResource =
            sharedSoilMesh ?: MeshResource.createBox(
                BoxOption(Vector3(PLOT_W, PLOT_T, PLOT_D))
                    .setCornerRadius(0.12f)
                    .setName("plot_soil")
            ).also { sharedSoilMesh = it }

        fun soilMaterial(): UnlitMaterial =
            sharedSoilMat ?: UnlitMaterial.create().apply {
                setBaseColor(Color4(0.60f, 0.45f, 0.34f, 1f))
            }.also { sharedSoilMat = it }

        fun grassMaterial(): UnlitMaterial =
            sharedGrassMat ?: UnlitMaterial.create().apply {
                setBaseColor(Color4(0.66f, 0.80f, 0.55f, 1f))
            }.also { sharedGrassMat = it }

        /** 各作物的显示缩放（模型原始尺寸不同）。 */
        fun cropScale(p: PlantDef): Float =
            when (p.assetBase) {
                "Watermelon", "Pumpkin" -> 0.45f
                "Corn", "Wheat" -> 0.5f
                "BushBerries" -> 0.55f
                "Tomato" -> 0.5f
                "Carrot" -> 0.5f
                else -> 0.55f
            }
    }
}
