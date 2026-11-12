package com.spatialfarm.game.scene

import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BoxOption
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.scene.audio.Sfx
import com.spatialfarm.game.scene.neighbor.LaoWangEntity
import com.spatialfarm.game.scene.plot.FarmPlotEntity
import com.spatialfarm.game.scene.props.QuestBoardEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 村口小院（视觉冲刺版 v2）。
 *
 * - 地形：RawMesh 起伏草原（TerrainMesh）
 * - 真 3D 模型：Quaternius CC0（crops/nature/village 包）
 * - 阳光：方向光 + 阴影
 * 布局：中前田垄 → 中央广场水井 → 北面主街（梅姨摊/胖婶食堂/锤叔铁匠铺）
 *       → 左后玩家小屋 → 右侧码头 → 边缘树林 → 远处风车
 */
class VillageEntity : Entity() {
    /** 田垄列表（HUD 快捷操作/测试用） */
    val plots = mutableListOf<FarmPlotEntity>()

    /** 邻居老王（供 HUD/偷菜逻辑查询状态） */
    var laoWang: LaoWangEntity? = null
        private set

    /** 场景级材质注册表：同一颜色只创建一次材质（资源复用规范）。
     *  注意：必须声明在 init 之前（Kotlin 按声明顺序初始化）。 */
    private val materials = mutableMapOf<String, Material>()

    private val ioScope = CoroutineScope(Dispatchers.Main)

    init {
        setUp()
    }

    private fun mat(key: String, color: Color4): Material =
        materials.getOrPut(key) {
            UnlitMaterial.create().apply { setBaseColor(color) }
        }

    private fun setUp() {
        setName("village_root")
        buildSky()
        buildTerrain()
        buildSun()
        buildFarmPlots()
        buildSign()
        buildDock()
        buildLaoWang()
        buildQuestBoard()
        buildAmbience()
        // 3D 模型异步加载（不阻塞首帧）
        ioScope.launch { loadModels() }
    }

    // ---------------------------------------------------------------- 广场委托板

    private fun buildQuestBoard() {
        val board = QuestBoardEntity()
        board.components[TransformComponent::class.java]!!.setPosition(Vector3(2.0f, 0f, -4.15f))
        board.components[TransformComponent::class.java]!!.setEulerAngles(EulerAngles(0f, -158f, 0f))
        addChild(board)
    }

    // ---------------------------------------------------------------- 环境音（鸟鸣来自树林方向）

    private fun buildAmbience() {
        val birds = Entity()
        birds.setName("ambient_birds")
        birds.components[TransformComponent::class.java]!!.setPosition(Vector3(-5.5f, 2.2f, -4.5f))
        Sfx.attachPointSource(birds, volume = 0.5f)
        addChild(birds)
        ioScope.launch {
            while (valid) {
                Sfx.playBirdAmbient(birds)
                delay(12_000) // birds.wav 时长 12s，循环
            }
        }
    }

    // ---------------------------------------------------------------- 邻居老王

    private fun buildLaoWang() {
        laoWang = LaoWangEntity(plots)
        addChild(laoWang!!)
        if (GameState.hasScarecrow) placeScarecrow()
    }

    /** 立稻草人（购买后调用）：在田边放一个草垛作为视觉标志 */
    fun placeScarecrow() {
        ioScope.launch {
            loadModel("models/village/Hay.glb", Vector3(-1.05f, 0f, PLOT_ROW_Z0 + 0.2f), scale = 0.55f)
        }
    }

    // ---------------------------------------------------------------- 天空球

    private fun buildSky() {
        val sky = ModelEntity(
            MeshResource.createSphere(SphereOption(55f).setName("sky_sphere")),
            UnlitMaterial.create().apply {
                setBaseColorTextureParameter(MaterialTextureParameter(TextureResource("textures/sky.png")))
                setCullingMode(MaterialCullingMode.NONE) // 双面：从球体内部可见
            }
        )
        sky.setName("sky_dome")
        sky.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, -2f, 0f))
        addChild(sky)
    }

    // ---------------------------------------------------------------- 地形与光照

    private fun buildTerrain() {
        val terrain = ModelEntity(
            TerrainMesh.create(),
            mat("terrain_base", Color4(1f, 1f, 1f, 1f)) // 白色基底 × 顶点色
        )
        terrain.setName("meadow_terrain")
        terrain.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0f, 0f))
        addChild(terrain)
    }

    private fun buildSun() {
        val sun = Entity()
        sun.setName("sun")
        sun.components[TransformComponent::class.java]?.apply {
            setEulerAngles(EulerAngles(-52f, -32f, 0f))
        }
        sun.components[DirectionalLightComponent::class.java] =
            DirectionalLightComponent(Color4(1f, 0.97f, 0.88f, 1f), 1.1f, true)
        addChild(sun)
    }

    // ---------------------------------------------------------------- 田垄（可交互种植位）

    private fun buildFarmPlots() {
        val group = Entity()
        group.setName("farm_plots")

        for (i in 0 until MAX_PLOTS) {
            val col = i % 4
            val row = i / 4
            val x = COL_XS[col]
            val z = PLOT_ROW_Z0 - row * ROW_GAP
            val plot = FarmPlotEntity(i, locked = i >= GameState.plotsOwned)
            plot.components[TransformComponent::class.java]!!.setPosition(Vector3(x, 0f, z))
            // 恢复存档中的作物状态
            GameState.plotSnapshots[i]?.let { snapshot -> plot.restore(snapshot) }
            plots.add(plot)
            group.addChild(plot)
        }
        addChild(group)
    }

    // ---------------------------------------------------------------- 迎宾木牌

    private fun buildSign() {
        val sign = Entity()
        sign.setName("welcome_sign")
        sign.components[TransformComponent::class.java]!!.setPosition(Vector3(-2.7f, 0f, -2.9f))

        val pole = ModelEntity(MeshResource.createCylinder(0.9f, 0.045f), mat("wood_dark", WOOD_DARK))
        pole.setName("sign_pole")
        pole.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.45f, 0f))
        sign.addChild(pole)

        val board = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(0.58f, 0.34f, 0.05f)).setCornerRadius(0.05f).setName("sign_board")
            ),
            mat("wood_dark", WOOD_DARK)
        )
        board.setName("sign_board_face")
        board.components[TransformComponent::class.java]!!.apply {
            setPosition(Vector3(0f, 0.82f, 0f))
            setEulerAngles(EulerAngles(0f, 28f, 0f))
        }
        sign.addChild(board)
        addChild(sign)
    }

    // ---------------------------------------------------------------- 码头

    private fun buildDock() {
        val dock = Entity()
        dock.setName("dock")
        dock.components[TransformComponent::class.java]!!.setPosition(Vector3(5.6f, 0f, -2.2f))

        val plankMesh = MeshResource.createBox(
            BoxOption(Vector3(1.2f, 0.12f, 0.55f)).setCornerRadius(0.04f).setName("dock_plank")
        )
        val plankMat = mat("plank", PLANK)
        for (i in 0 until 3) {
            val plank = ModelEntity(plankMesh, plankMat)
            plank.setName("dock_plank_$i")
            plank.components[TransformComponent::class.java]!!
                .setPosition(Vector3(0f, 0.1f, -i * 0.65f))
            dock.addChild(plank)
        }
        addChild(dock)
    }

    // ---------------------------------------------------------------- 田垄围栏（几何体）

    private fun buildPlotFence() {
        val fence = Entity()
        fence.setName("plot_fence")
        val railMat = mat("wood", WOOD)

        val halfW = 4.25f
        val halfD = 1.5f
        val centerZ = (PLOT_ROW_Z0 + (PLOT_ROW_Z0 - ROW_GAP)) / 2f
        val railMeshX = MeshResource.createBox(
            BoxOption(Vector3(halfW * 2 + 0.25f, 0.055f, 0.055f)).setCornerRadius(0.02f).setName("rail_x")
        )
        val railMeshZ = MeshResource.createBox(
            BoxOption(Vector3(halfD * 2 + 0.1f, 0.055f, 0.055f)).setCornerRadius(0.02f).setName("rail_z")
        )
        val postMesh = MeshResource.createBox(
            BoxOption(Vector3(0.085f, 0.4f, 0.085f)).setCornerRadius(0.02f).setName("fence_post")
        )
        val railY = 0.3f

        fun rail(mesh: MeshResource, pos: Vector3, yaw: Float) {
            val e = ModelEntity(mesh, railMat)
            e.setName("rail")
            e.components[TransformComponent::class.java]!!.apply {
                setPosition(pos)
                setEulerAngles(EulerAngles(0f, yaw, 0f))
            }
            fence.addChild(e)
        }
        rail(railMeshX, Vector3(0f, railY, centerZ - halfD), 0f)
        rail(railMeshX, Vector3(0f, railY, centerZ + halfD), 0f)
        rail(railMeshZ, Vector3(-halfW, railY, centerZ), 90f)
        rail(railMeshZ, Vector3(halfW, railY, centerZ), 90f)

        var n = 0
        fun post(x: Float, z: Float) {
            val e = ModelEntity(postMesh, railMat)
            e.setName("post_$n")
            e.components[TransformComponent::class.java]!!.setPosition(Vector3(x, 0.2f, z))
            fence.addChild(e)
            n++
        }
        for ((x, z) in listOf(
            -halfW to centerZ - halfD, -halfW / 4 to centerZ - halfD,
            0f to centerZ - halfD, halfW / 4 to centerZ - halfD, halfW to centerZ - halfD,
            -halfW to centerZ + halfD, 0f to centerZ + halfD, halfW to centerZ + halfD,
            -halfW to centerZ, halfW to centerZ,
        )) post(x, z)

        addChild(fence)
    }

    // ---------------------------------------------------------------- 3D 模型加载

    private suspend fun loadModels() {
        // ---- 主街店铺（北）----
        loadModel("models/village/MarketStand_1.glb", Vector3(-2.6f, 0f, -6.2f), yaw = 180f, scale = 0.55f)   // 梅姨杂货摊
        loadModel("models/village/Inn.glb", Vector3(0f, 0f, -6.6f), yaw = 180f, scale = 0.42f)                // 胖婶食堂
        loadModel("models/village/Blacksmith.glb", Vector3(2.8f, 0f, -6.2f), yaw = 180f, scale = 0.42f)       // 锤叔铁匠铺

        // ---- 玩家小屋（左后）----
        loadModel("models/village/House_1.glb", Vector3(-4.2f, 0f, -1.2f), yaw = 115f, scale = 0.40f)
        loadModel("models/village/Barrel.glb", Vector3(-3.2f, 0f, -0.4f), scale = 0.6f)
        loadModel("models/village/Crate.glb", Vector3(-5.1f, 0f, -0.6f), scale = 0.6f)

        // ---- 中央广场 ----
        loadModel("models/village/Well.glb", Vector3(0f, 0f, -4.2f), scale = 0.6f)
        loadModel("models/village/Hay.glb", Vector3(3.6f, 0f, -4.6f), scale = 0.5f)
        loadModel("models/village/Cart.glb", Vector3(-1.9f, 0f, -4.9f), yaw = 70f, scale = 0.55f)

        // ---- 石板小路：田垄 → 广场 → 主街 ----
        loadModel("models/village/Path_Square.glb", Vector3(0f, 0.02f, -3.5f), scale = 0.8f)
        loadModel("models/village/Path_Straight.glb", Vector3(0f, 0.02f, -4.9f), scale = 0.8f)
        loadModel("models/village/Path_Straight.glb", Vector3(0f, 0.02f, -5.6f), scale = 0.8f)

        // ---- 远处风车（地标）----
        loadModel("models/village/Mill.glb", Vector3(6.8f, 0f, -7.8f), yaw = -30f, scale = 0.5f)

        // ---- 田垄简易围栏（低矮木栏，几何体拼装更整齐） ----
        buildPlotFence()

        // ---- 边缘树林与灌木（环绕氛围） ----
        loadModel("models/nature/CommonTree_1.glb", Vector3(-6.4f, 0f, -5.4f), scale = 1.15f)
        loadModel("models/nature/CommonTree_3.glb", Vector3(-6.9f, 0f, -2.6f), scale = 1.0f)
        loadModel("models/nature/CommonTree_2.glb", Vector3(-6.2f, 0f, 0.8f), scale = 1.25f)
        loadModel("models/nature/CommonTree_4.glb", Vector3(5.9f, 0f, -0.6f), scale = 1.1f)
        loadModel("models/nature/CommonTree_5.glb", Vector3(6.6f, 0f, -4.8f), scale = 1.0f)
        loadModel("models/nature/PineTree_1.glb", Vector3(-3.4f, 0f, -8.2f), scale = 1.1f)
        loadModel("models/nature/PineTree_2.glb", Vector3(3.9f, 0f, -8.4f), scale = 1.2f)
        loadModel("models/nature/PineTree_3.glb", Vector3(-0.8f, 0f, -8.8f), scale = 1.0f)
        loadModel("models/nature/Bush_1.glb", Vector3(-2.6f, 0f, 1.0f))
        loadModel("models/nature/Bush_2.glb", Vector3(2.8f, 0f, 0.6f))
        loadModel("models/nature/Rock_1.glb", Vector3(4.6f, 0f, -5.8f))
        loadModel("models/nature/Rock_2.glb", Vector3(-5.4f, 0f, -4.0f))
        loadModel("models/nature/Rock_3.glb", Vector3(1.6f, 0f, 0.9f))
    }

    /**
     * 加载一个 GLB 模型并放置到村中。
     * @param uri assets 内路径（不含 asset:// 前缀）
     */
    private suspend fun loadModel(
        uri: String,
        position: Vector3,
        yaw: Float = 0f,
        scale: Float = 1f,
    ) {
        val entity = Entity.loadSuspend("asset://$uri")
        if (!valid) {
            entity.destroy(true)
            return
        }
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(position)
            setEulerAngles(EulerAngles(0f, yaw, 0f))
            setScaleVector(Vector3(scale, scale, scale))
        }
        addChild(entity)
    }

    private companion object {
        // ---- 尺寸与布局常量（米） ----
        const val MAX_PLOTS = 8
        const val PLOT_GAP = 1.9f
        const val PLOT_ROW_Z0 = -2.0f
        const val ROW_GAP = 1.35f
        val COL_XS = floatArrayOf(-2.85f, -0.95f, 0.95f, 2.85f)

        // ---- 粉彩调色板（保留几何体用） ----
        val SOIL = Color4(0.60f, 0.45f, 0.34f, 1f)       // 田垄棕
        val WOOD = Color4(0.87f, 0.74f, 0.58f, 1f)       // 原木
        val WOOD_DARK = Color4(0.55f, 0.42f, 0.30f, 1f)  // 深木
        val PLANK = Color4(0.82f, 0.69f, 0.52f, 1f)      // 栈木板
    }
}
