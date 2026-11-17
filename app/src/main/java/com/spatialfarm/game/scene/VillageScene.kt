package com.spatialfarm.game.scene

import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationTransitionMode
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
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.TripoOverrides
import com.spatialfarm.game.scene.audio.Sfx
import com.spatialfarm.game.scene.fx.AmbientLife
import com.spatialfarm.game.scene.family.ChildEntity
import com.spatialfarm.game.scene.family.ChildKind
import com.spatialfarm.game.scene.neighbor.LaoWangEntity
import com.spatialfarm.game.scene.plot.FarmPlotEntity
import com.spatialfarm.game.scene.props.QuestBoardEntity
import com.spatialfarm.game.scene.props.ShopKind
import com.spatialfarm.game.scene.props.ShopPointEntity
import com.spatialfarm.game.scene.props.ShopkeeperEntity
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

    /** 家里的两个孩子（可点按帮忙） */
    val children = mutableListOf<ChildEntity>()

    /** 场景级材质注册表：同一颜色只创建一次材质（资源复用规范）。
     *  注意：必须声明在 init 之前（Kotlin 按声明顺序初始化）。 */
    private val materials = mutableMapOf<String, Material>()

    private val ioScope = CoroutineScope(Dispatchers.Main)

    // 天气字段（必须声明在 init 之前：setUp 里会用到）
    private var rainRoot: Entity? = null
    private val rainLayers = mutableListOf<Entity>()
    private var rainOn = false
    private var skyRain: Entity? = null
    private var weatherLoopStarted = false

    init {
        setUp()
    }

    private fun mat(key: String, color: Color4): Material =
        materials.getOrPut(key) {
            UnlitMaterial.create().apply {
                setBaseColor(color)
                toGlobal()
            }
        }

    private fun setUp() {
        setName("village_root")
        buildSky()
        buildTerrain()
        buildGrass()
        buildWeather()
        buildSun()
        buildFarmPlots()
        buildSign()
        buildDock()
        buildLaoWang()
        buildChildren()
        buildQuestBoard()
        buildShopPoints()
        buildAmbience()
        spawnWildlife()
        buildSeaAndLakes()
        buildMountains()
        scatterWildIsland()
        startBgm()
        Locomotion.start()
        ioScope.launch { locomotionLoop() }
        // 3D 模型异步加载（不阻塞首帧）
        ioScope.launch { loadModels() }
        // 昼夜循环
        ioScope.launch { clockLoop() }
        // 开场揭示动画（缩小 → 生长到真实尺度）
        if (!GameState.demoMode) playIntroReveal()
    }

    // ---------------------------------------------------------------- 孩子（小勇/小花）

    private fun buildChildren() {
        val daughter = ChildEntity(ChildKind.DAUGHTER) { plots }
        daughter.components[TransformComponent::class.java]!!.setPosition(Vector3(-5.9f, 0f, 1.0f))
        addChild(daughter)
        children.add(daughter)

        val son = ChildEntity(ChildKind.SON) { plots }
        son.components[TransformComponent::class.java]!!.setPosition(Vector3(-5.2f, 0f, 1.8f))
        addChild(son)
        children.add(son)
    }

    // ---------------------------------------------------------------- 店铺交互点（点房子开店）

    private fun buildShopPoints() {
        shopPoint(ShopKind.SEED, Vector3(-5.0f, 0f, -8.2f), Vector3(3.6f, 2.4f, 3.6f))
        shopPoint(ShopKind.BUYER, Vector3(0f, 0f, -8.9f), Vector3(2.9f, 2.6f, 2.9f))
        shopPoint(ShopKind.TOOL, Vector3(5.0f, 0f, -8.2f), Vector3(3.4f, 2.4f, 3.8f))
    }

    private fun shopPoint(kind: ShopKind, base: Vector3, size: Vector3) {
        val e = ShopPointEntity(kind, size)
        e.components[TransformComponent::class.java]!!
            .setPosition(Vector3(base.x, size.y / 2f, base.z))
        addChild(e)
    }

    // ---------------------------------------------------------------- 环海 / 湖泊 / 溪流

    private fun waterMat(key: String, color: Color4): UnlitMaterial =
        UnlitMaterial.create().apply {
            setBaseColor(color)
            toGlobal()
        }.also { materials.getOrPut(key) { it } }

    private fun buildSeaAndLakes() {
        val seaMat = waterMat("sea", Color4(0.36f, 0.70f, 0.84f, 1f))
        val lakeMat = waterMat("lake", Color4(0.40f, 0.74f, 0.88f, 1f))

        val sea = ModelEntity(
            MeshResource.createBox(BoxOption(Vector3(420f, 0.06f, 420f)).setName("sea")),
            seaMat
        )
        sea.setName("sea_surface")
        sea.components[TransformComponent::class.java]!!
            .setPosition(Vector3(0f, TerrainMesh.SEA_LEVEL - 0.03f, 0f))
        addChild(sea)

        val foam = ModelEntity(
            MeshResource.createTorus(48.5f, 47.4f),
            UnlitMaterial.create().apply {
                setBaseColor(Color4(0.97f, 1f, 1f, 1f))
                toGlobal()
            }
        )
        foam.setName("shore_foam")
        // 岸线泡沫环看起来像海上的白带，先不加入场景
        foamEntity = null
        foam.components[TransformComponent::class.java]!!
            .setPosition(Vector3(0f, TerrainMesh.SEA_LEVEL + 0.02f, 0f))

        val lake = ModelEntity(
            MeshResource.createBox(BoxOption(Vector3(27f, 0.05f, 25f)).setName("lake")),
            lakeMat
        )
        lake.setName("lake_surface")
        lake.components[TransformComponent::class.java]!!
            .setPosition(Vector3(-20f, TerrainMesh.LAKE_LEVEL, 16f))
        addChild(lake)

        val path = TerrainMesh.streamSegments()
        val segMat = waterMat("stream", Color4(0.50f, 0.84f, 0.95f, 1f))
        for (i in 0 until path.size - 1) {
            val x1 = path[i][0]
            val z1 = path[i][1]
            val x2 = path[i + 1][0]
            val z2 = path[i + 1][1]
            val dx = x2 - x1
            val dz = z2 - z1
            val len = kotlin.math.hypot(dx, dz) + 1.2f
            val yaw = Math.toDegrees(kotlin.math.atan2(dx, dz).toDouble()).toFloat()
            val seg = ModelEntity(
                MeshResource.createBox(BoxOption(Vector3(2.6f, 0.05f, len)).setName("stream_seg")),
                segMat
            )
            seg.setName("stream_$i")
            seg.components[TransformComponent::class.java]!!.apply {
                setPosition(Vector3((x1 + x2) / 2f, -0.13f, (z1 + z2) / 2f))
                setEulerAngles(EulerAngles(0f, yaw, 0f))
            }
            addChild(seg)
        }

        startWaterTicker()
    }

    // ---------------------------------------------------------------- 水面呼吸动画

    private var waterTickerStarted = false
    private var foamEntity: Entity? = null

    private fun startWaterTicker() {
        if (waterTickerStarted) return
        waterTickerStarted = true
        ioScope.launch {
            var t = 0f
            while (valid) {
                delay(120)
                t += 0.12f
                val k = 0.5f + 0.5f * kotlin.math.sin(t * 0.8f)
                (materials["sea"] as? UnlitMaterial)?.setBaseColor(
                    lerpColorV(Color4(0.35f, 0.68f, 0.83f, 1f), Color4(0.40f, 0.75f, 0.88f, 1f), k)
                )
                (materials["lake"] as? UnlitMaterial)?.setBaseColor(
                    lerpColorV(Color4(0.39f, 0.72f, 0.86f, 1f), Color4(0.45f, 0.79f, 0.91f, 1f), k)
                )
                (materials["stream"] as? UnlitMaterial)?.setBaseColor(
                    lerpColorV(Color4(0.47f, 0.80f, 0.92f, 1f), Color4(0.53f, 0.85f, 0.95f, 1f), k)
                )
                foamEntity?.takeIf { it.valid }?.components?.get(TransformComponent::class.java)?.apply {
                    val s = 1f + 0.004f * kotlin.math.sin(t * 1.6f)
                    setScaleVector(Vector3(s, 1f, s))
                }
            }
        }
    }

    private fun lerpColorV(a: Color4, b: Color4, t: Float): Color4 {
        val k = t.coerceIn(0f, 1f)
        return Color4(
            a.red + (b.red - a.red) * k,
            a.green + (b.green - a.green) * k,
            a.blue + (b.blue - a.blue) * k,
            1f,
        )
    }

    // ---------------------------------------------------------------- 远山天际线

    private fun buildMountains() {
        val vertexColorMat = mat("mountain_vertexcolor", Color4(1f, 1f, 1f, 1f))
        val meshes = (0 until 3).map { seed -> MountainMesh.create(seed).also { it.toGlobal() } }
        val rnd = kotlin.random.Random(7)
        for (i in 0 until 10) {
            val angle = (i / 10f) * Math.PI.toFloat() * 2f + rnd.nextFloat() * 0.35f
            val dist = 165f + rnd.nextFloat() * 70f
            val h = 42f + rnd.nextFloat() * 45f
            val r = h * (0.62f + rnd.nextFloat() * 0.25f)
            val x = kotlin.math.cos(angle) * dist
            val z = kotlin.math.sin(angle) * dist

            val mountain = ModelEntity(meshes[i % 3], vertexColorMat)
            mountain.setName("mountain_$i")
            mountain.components[TransformComponent::class.java]!!.apply {
                setPosition(Vector3(x, TerrainMesh.SEA_LEVEL, z))
                setEulerAngles(EulerAngles(0f, rnd.nextFloat() * 360f, 0f))
                setScaleVector(Vector3(r, h, r))
            }
            addChild(mountain)
        }
    }

    // ---------------------------------------------------------------- 岛屿散布（花/树/石/路）

    private fun scatterWildIsland() {
        ioScope.launch {
            val rnd = kotlin.random.Random(42)
            val path = TerrainMesh.streamSegments()

            fun randPoint(): FloatArray {
                while (true) {
                    val a = rnd.nextFloat() * 6.2832f
                    val r = 18f + rnd.nextFloat() * 25f
                    val x = kotlin.math.cos(a) * r
                    val z = kotlin.math.sin(a) * r
                    val dLake = kotlin.math.hypot(x + 20f, z - 16f)
                    if (dLake < 13f) continue
                    var nearStream = false
                    for (i in 0 until path.size - 1) {
                        val d = pointSegDist(x, z, path[i][0], path[i][1], path[i + 1][0], path[i + 1][1])
                        if (d < 3.5f) {
                            nearStream = true
                            break
                        }
                    }
                    if (nearStream) continue
                    return floatArrayOf(x, z)
                }
            }

            val flowerAssets = listOf("Flower_1", "Flower_2", "Flower_3", "Flower_4")
            repeat(26) { idx ->
                val pnt = randPoint()
                val fa = flowerAssets[rnd.nextInt(flowerAssets.size)]
                val itemId = if (idx % 2 == 0) "prop_flower_a" else "prop_flower_b"
                loadModelSmart(
                    itemId,
                    "models/crops/$fa.glb",
                    Vector3(pnt[0], TerrainMesh.height(pnt[0], pnt[1]), pnt[1]),
                    yaw = rnd.nextFloat() * 360f,
                    scale = 0.3f + rnd.nextFloat() * 0.18f,
                )
            }

            repeat(18) {
                val pnt = randPoint()
                val isPine = rnd.nextBoolean()
                loadSwaySmart(
                    if (isPine) "prop_tree_pine" else if (rnd.nextBoolean()) "prop_tree_a" else "prop_tree_b",
                    if (isPine) "models/nature/PineTree_${1 + rnd.nextInt(3)}.glb"
                    else "models/nature/CommonTree_${1 + rnd.nextInt(5)}.glb",
                    Vector3(pnt[0], TerrainMesh.height(pnt[0], pnt[1]), pnt[1]),
                    scale = 0.9f + rnd.nextFloat() * 0.5f,
                )
            }

            repeat(10) {
                val pnt = randPoint()
                loadModelSmart(
                    "prop_rock",
                    "models/nature/Rock_${1 + rnd.nextInt(4)}.glb",
                    Vector3(pnt[0], TerrainMesh.height(pnt[0], pnt[1]), pnt[1]),
                    yaw = rnd.nextFloat() * 360f,
                    scale = 0.6f + rnd.nextFloat() * 0.5f,
                )
            }
            repeat(8) {
                val pnt = randPoint()
                loadModelSmart(
                    "prop_bush",
                    "models/nature/Bush_${1 + rnd.nextInt(2)}.glb",
                    Vector3(pnt[0], TerrainMesh.height(pnt[0], pnt[1]), pnt[1]),
                    yaw = rnd.nextFloat() * 360f,
                    scale = 0.8f + rnd.nextFloat() * 0.4f,
                )
            }

            buildTrail(
                listOf(
                    floatArrayOf(-2f, -3f), floatArrayOf(-7f, 2f), floatArrayOf(-12f, 7f),
                    floatArrayOf(-16f, 11f), floatArrayOf(-18f, 13.5f),
                )
            )
            buildTrail(
                listOf(
                    floatArrayOf(3f, -4f), floatArrayOf(9f, -7f), floatArrayOf(15f, -10f),
                    floatArrayOf(20f, -13f), floatArrayOf(23f, -15f),
                )
            )
            buildTrail(
                listOf(
                    floatArrayOf(2f, 1f), floatArrayOf(4f, 8f), floatArrayOf(6f, 15f),
                    floatArrayOf(7f, 22f),
                )
            )
        }
    }

    private suspend fun buildTrail(points: List<FloatArray>) {
        for (i in 0 until points.size - 1) {
            val x1 = points[i][0]
            val z1 = points[i][1]
            val x2 = points[i + 1][0]
            val z2 = points[i + 1][1]
            val dx = x2 - x1
            val dz = z2 - z1
            val len = kotlin.math.hypot(dx, dz)
            val steps = kotlin.math.ceil(len).toInt().coerceAtLeast(1)
            val yaw = Math.toDegrees(kotlin.math.atan2(dx, dz).toDouble()).toFloat()
            for (st in 0 until steps) {
                val t = st / steps.toFloat()
                val px = x1 + dx * t
                val pz = z1 + dz * t
                loadModelSmart(
                    "prop_path_straight",
                    "models/village/Path_Straight.glb",
                    Vector3(px, TerrainMesh.height(px, pz) + 0.03f, pz),
                    yaw = yaw,
                    scale = 0.8f,
                )
            }
        }
    }

    private fun pointSegDist(
        px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float,
    ): Float {
        val dx = x2 - x1
        val dz = z2 - z1
        val len2 = dx * dx + dz * dz
        if (len2 <= 0.0001f) return kotlin.math.hypot(px - x1, pz - z1)
        var t = ((px - x1) * dx + (pz - z1) * dz) / len2
        t = t.coerceIn(0f, 1f)
        return kotlin.math.hypot(px - (x1 + t * dx), pz - (z1 + t * dz))
    }

    // ---------------------------------------------------------------- 移动（把世界搬到玩家脚下）

    private suspend fun locomotionLoop() {
        var last = System.currentTimeMillis()
        while (valid) {
            kotlinx.coroutines.delay(33)
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000f).coerceAtMost(0.1f)
            last = now
            if (scaleCurrent < 0.9f) continue
            val offset = Locomotion.tick(dt)
            applyRootTransform(offset)
        }
    }

    private fun applyRootTransform(offset: Vector3) {
        val lift = 1.05f * (1f - scaleCurrent)
        components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(offset.x, offset.y + lift, offset.z))
            setScaleVector(Vector3(scaleCurrent, scaleCurrent, scaleCurrent))
        }
    }

    // ---------------------------------------------------------------- 世界生命（飞鸟/蝴蝶/炊烟）

    private fun spawnWildlife() {
        // 两只小鸟绕岛飞
        AmbientLife.spawnBird(
            this, Vector3(0f, 0f, -2.5f), radius = 6.5f, height = 5.2f,
            speed = 0.35f, phase = 0f, wingColor = Color4(0.45f, 0.40f, 0.35f, 1f),
        )
        AmbientLife.spawnBird(
            this, Vector3(0f, 0f, -2.5f), radius = 5.2f, height = 6.0f,
            speed = 0.28f, phase = 2.4f, wingColor = Color4(0.55f, 0.50f, 0.45f, 1f),
        )
        // 田边两只蝴蝶
        AmbientLife.spawnButterfly(this, Vector3(0.2f, 0.35f, -1.8f), Color4(0.98f, 0.85f, 0.45f, 1f))
        AmbientLife.spawnButterfly(this, Vector3(-1.2f, 0.35f, -3.2f), Color4(0.95f, 0.65f, 0.75f, 1f))
        // 炊烟：玩家小屋 + 客栈
        AmbientLife.chimneySmoke(this, Vector3(-3.9f, 1.85f, -1.4f))
        AmbientLife.chimneySmoke(this, Vector3(0.35f, 2.15f, -6.5f))
    }

    private fun startBgm() {
        val bgm = Entity()
        bgm.setName("bgm_player")
        bgm.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.2f, -2f))
        Sfx.attachPointSource(bgm, volume = 0.30f)
        addChild(bgm)
        ioScope.launch {
            delay(2600) // 等开场动画
            while (valid) {
                Sfx.playBgm(bgm)
                delay(16_000) // bgm.wav 16 秒循环
            }
        }
    }

    /** 开场揭示：小岛先以小尺度悬浮在前方，随后生长到真实大小把你"包"进村庄。 */
    fun playIntroReveal() {
        ioScope.launch {
            applyDioramaScale(0.16f)
            kotlinx.coroutines.delay(500)
            val steps = 40
            val from = 0.16f
            for (i in 1..steps) {
                if (!valid) return@launch
                val t = i / steps.toFloat()
                val eased = t * t * (3 - 2 * t)
                applyDioramaScale(from + (1f - from) * eased)
                kotlinx.coroutines.delay(55)
            }
            applyDioramaScale(1f)
            GameState.postHint("🏝️ 欢迎来到清风岛！从一块田开始你的田园生活 🌱")
        }
    }

    // ---------------------------------------------------------------- 广场委托板

    private fun buildQuestBoard() {
        val board = QuestBoardEntity()
        board.components[TransformComponent::class.java]!!.setPosition(Vector3(-3.3f, 0f, -4.7f))
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

    // ---------------------------------------------------------------- 昼夜循环

    private var clockSeconds = 0f
    private var lastPhase = "day"

    private suspend fun clockLoop() {
        while (valid) {
            kotlinx.coroutines.delay(2_000)
            clockSeconds += 2f
            applyClock()
        }
    }

    private fun applyClock() {
        // 8 分钟一天：白天 0-55%，黄昏 55-65%，夜晚 65-90%，清晨 90-100%
        val t = (clockSeconds % DAY_PERIOD) / DAY_PERIOD
        val phase = when {
            t < 0.55f -> "day"
            t < 0.65f -> "dusk"
            t < 0.90f -> "night"
            else -> "dawn"
        }
        if (phase != lastPhase) {
            lastPhase = phase
            when (phase) {
                "dusk" -> GameState.postHint("🌇 太阳下山了，黄昏的村庄真好看")
                "night" -> GameState.postHint("🌙 夜深了。夜里作物也在悄悄长大")
                "dawn" -> GameState.postHint("🌅 清晨，新的一天开始")
            }
        }
        // 太阳：仰角随时间转一圈；强度按相位
        val elevation = -90f + t * 360f
        sunEntity?.components?.get(TransformComponent::class.java)?.apply {
            setEulerAngles(EulerAngles(elevation, -32f, 0f))
        }
        val intensity = when (phase) {
            "day" -> 1.1f
            "dusk" -> 0.55f
            "night" -> 0.06f
            else -> 0.5f
        }
        sunEntity?.components?.get(DirectionalLightComponent::class.java)?.intensity = intensity
        // 天空切换
        skyDay?.enabled = phase == "day" || phase == "dawn"
        skyDusk?.enabled = phase == "dusk"
        skyNight?.enabled = phase == "night"
    }

    private var diorama = false
    private var dioramaJob: kotlinx.coroutines.Job? = null

    /** 沙盘模式开关：村庄缩小到桌面尺度（天空球反向补偿保持包裹） */
    fun toggleDiorama() {
        diorama = !diorama
        val target = if (diorama) 0.22f else 1f
        GameState.postHint(if (diorama) "🔭 沙盘视角：俯瞰整个村庄" else "回到真实尺度")
        dioramaJob?.cancel()
        dioramaJob = ioScope.launch {
            val start = scaleCurrent
            val steps = 24
            repeat(steps + 1) { i ->
                val t = i.toFloat() / steps
                val eased = t * t * (3 - 2 * t)
                val s = start + (target - start) * eased
                applyDioramaScale(s)
                kotlinx.coroutines.delay(25)
            }
        }
    }

    private var scaleCurrent = 1f

    private fun applyDioramaScale(s: Float) {
        scaleCurrent = s
        applyRootTransform(Vector3(-Locomotion.playerX, -Locomotion.groundHeight, -Locomotion.playerZ))
        skyEntity?.components?.get(TransformComponent::class.java)?.apply {
            val inv = 1f / s
            setScaleVector(Vector3(inv, inv, inv))
            setPosition(Vector3(0f, -2f * inv, 0f))
        }
    }

    /** 田垄世界坐标锚点（动作菜单浮窗定位用） */
    fun plotAnchor(index: Int): Vector3 {
        val col = index % 4
        val row = index / 4
        val x = if (col in COL_XS.indices) COL_XS[col] else 0f
        val z = PLOT_ROW_Z0 - row * ROW_GAP
        return Vector3(x, 1.1f, z)
    }

    /** 立稻草人（购买后调用）：在田边放一个草垛作为视觉标志 */
    fun placeScarecrow() {
        ioScope.launch {
            loadModelSmart("prop_hay", "models/village/Hay.glb", Vector3(-1.05f, 0f, PLOT_ROW_Z0 + 0.2f), scale = 0.55f)
        }
    }

    // ---------------------------------------------------------------- 天空球

    private var skyEntity: Entity? = null
    private var skyDay: Entity? = null
    private var skyDusk: Entity? = null
    private var skyNight: Entity? = null
    private var sunEntity: Entity? = null

    private fun makeSkySphere(name: String, tint: Color4): Entity {
        val sky = ModelEntity(
            MeshResource.createSphere(SphereOption(520f).setName("sky_sphere_$name")),
            UnlitMaterial.create().apply {
                setBaseColor(tint)
                setBaseColorTextureParameter(MaterialTextureParameter(TextureResource("textures/sky.png")))
                setCullingMode(MaterialCullingMode.NONE) // 双面：从球体内部可见
            }
        )
        sky.setName(name)
        sky.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, -2f, 0f))
        addChild(sky)
        return sky
    }

    private fun buildSky() {
        skyDay = makeSkySphere("sky_day", Color4(1f, 1f, 1f, 1f))
        skyDusk = makeSkySphere("sky_dusk", Color4(1.0f, 0.78f, 0.66f, 1f)).also { it.enabled = false }
        skyNight = makeSkySphere("sky_night", Color4(0.30f, 0.36f, 0.55f, 1f)).also { it.enabled = false }
        skyRain = makeSkySphere("sky_rain", Color4(0.58f, 0.64f, 0.72f, 1f)).also { it.enabled = false }
        skyEntity = skyDay
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

    /** 程序化草地（单网格，一次 draw call） */
    private fun buildGrass() {
        val grassMat = UnlitMaterial.create().apply {
            setBaseColor(Color4(1f, 1f, 1f, 1f))
            setCullingMode(MaterialCullingMode.NONE)
            toGlobal()
        }
        val grass = ModelEntity(GrassMesh.create(), grassMat)
        grass.setName("meadow_grass")
        addChild(grass)
    }

    // ---------------------------------------------------------------- 天气（雨）

    private fun buildWeather() {
        val root = com.spatialfarm.game.scene.fx.RainMesh.createRain()
        root.enabled = false
        rainRoot = root
        rainLayers.clear()
        rainLayers.addAll(root.getChildren().filterNotNull())
        addChild(root)
        startWeatherLoop()
    }

    /** 切换下雨（供 HUD 按钮调用） */
    fun toggleRain(): Boolean {
        rainOn = !rainOn
        rainRoot?.enabled = rainOn
        skyRain?.enabled = rainOn
        skyDay?.enabled = !rainOn
        if (rainOn) {
            GameState.postHint("🌧 下雨了：作物会自己喝水，长得更快")
        } else {
            GameState.postHint("☀️ 雨停了，适合去田里看看")
        }
        return rainOn
    }

    private fun startWeatherLoop() {
        if (weatherLoopStarted) return
        weatherLoopStarted = true
        ioScope.launch {
            var t = 0f
            while (valid) {
                delay(50)
                t += 0.05f
                // 雨幕下落 + 循环
                rainLayers.forEach { layer ->
                    if (!layer.valid) return@forEach
                    val tr = layer.components[TransformComponent::class.java] ?: return@forEach
                    val p = tr.position
                    var y = p.y - 7f * 0.05f
                    if (y < -2f) y += 10f
                    tr.position = Vector3(p.x, y, p.z)
                }
                // 下雨时作物缓慢自动补水生长（每秒一次）
                if (rainOn && (t * 20).toInt() % 20 == 0) {
                    plots.forEach { it.rainBoost() }
                }
            }
        }
    }

    private fun buildSun() {
        val sun = Entity()
        sun.setName("sun")
        sun.components[TransformComponent::class.java]?.apply {
            setEulerAngles(EulerAngles(-52f, -32f, 0f))
        }
        // 吉卜力暖阳：暖金色主光
        sun.components[DirectionalLightComponent::class.java] =
            DirectionalLightComponent(Color4(1f, 0.95f, 0.84f, 1f), 1.3f, true)
        sunEntity = sun
        addChild(sun)

        // 天光补光：冷色补光让阴影泛蓝（吉卜力式）
        val fill = Entity()
        fill.setName("fill_light")
        fill.components[TransformComponent::class.java]?.apply {
            setEulerAngles(EulerAngles(-30f, 140f, 0f))
        }
        fill.components[DirectionalLightComponent::class.java] =
            DirectionalLightComponent(Color4(0.62f, 0.75f, 1.0f, 1f), 0.35f, false)
        addChild(fill)
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
        // 广场到两侧店铺的小路
        buildTrail(
            listOf(
                floatArrayOf(0f, -6.3f), floatArrayOf(-2.5f, -6.6f), floatArrayOf(-4.4f, -6.8f),
            )
        )
        buildTrail(
            listOf(
                floatArrayOf(0f, -6.3f), floatArrayOf(2.5f, -6.6f), floatArrayOf(4.4f, -6.8f),
            )
        )

        // ---- 店主 NPC（待机动画 + 可点击开店）----
        run {
            fun shopkeeper(itemId: String, kind: ShopKind, x: Float, z: Float, yaw: Float) {
                val npc = ShopkeeperEntity(itemId, kind, yaw = yaw, scale = 1f)
                npc.components[TransformComponent::class.java]!!.setPosition(Vector3(x, 0f, z))
                addChild(npc)
            }
            shopkeeper("mei_yi", ShopKind.SEED, -5.0f, -6.5f, 135f)
            shopkeeper("pang_shen", ShopKind.BUYER, 0f, -7.2f, 180f)
            shopkeeper("chui_shu", ShopKind.TOOL, 5.0f, -6.5f, -135f)
        }

        // ---- 海边木码头（参考图版）----
        loadOverrideOnly(
            "bld_dock",
            Vector3(6.0f, -0.25f, 41.0f),
            yaw = 180f,
            scale = 1.0f,
        )

        // ---- 栈桥水车（参考图版）：湖边木栈道 + 水轮 + 小船 ----
        loadOverrideOnly(
            "bld_watermill_dock",
            Vector3(-12.6f, -0.35f, 12.0f),
            yaw = 35f,
            scale = 1.0f,
        )

        // ---- 风车丘上的石塔风车（参考图版）----
        run {
            val wx = 23.0f
            val wz = -15.0f
            loadOverrideOnly(
                "env_windmill",
                Vector3(wx, TerrainMesh.height(wx, wz) + 0.02f, wz),
                yaw = 30f,
                scale = 1.0f,
            )
        }

        // ---- Tripo 大树：出生点右前 + 湖畔 ----
        run {
            val tx = 5.2f
            val tz = 1.8f
            loadModelSmart(
                "prop_tree_big",
                "models/nature/CommonTree_2.glb",
                Vector3(tx, TerrainMesh.height(tx, tz), tz),
                yaw = 25f,
                scale = 1.6f,
            )
        }
        run {
            val tx = -16.5f
            val tz = 21.5f
            loadModelSmart(
                "prop_tree_big",
                "models/nature/CommonTree_4.glb",
                Vector3(tx, TerrainMesh.height(tx, tz), tz),
                yaw = -40f,
                scale = 1.35f,
            )
        }

        // ---- 主街店铺（北） ----
        loadModelSmart("bld_market", "models/village/MarketStand_1.glb", Vector3(-5.0f, 0f, -8.2f), yaw = 45f, scale = 0.55f)   // 梅姨种子铺
        loadModelSmart("bld_inn", "models/village/Inn.glb", Vector3(0f, 0f, -8.9f), yaw = 0f, scale = 0.42f)                // 胖婶食堂
        loadModelSmart("bld_workshop", "models/village/Blacksmith.glb", Vector3(5.0f, 0f, -8.2f), yaw = -45f, scale = 0.42f)       // 锤叔铁匠铺

        // ---- 玩家小屋（左后）----
        loadModelSmart("bld_house", "models/village/House_1.glb", Vector3(-8.8f, 0f, 0.6f), yaw = 90f, scale = 0.40f)
        loadModelSmart("prop_barrel", "models/village/Barrel.glb", Vector3(-3.2f, 0f, -0.4f), scale = 0.6f)
        loadModelSmart("prop_crate", "models/village/Crate.glb", Vector3(-5.1f, 0f, -0.6f), scale = 0.6f)

        // ---- 中央广场 ----
        loadModelSmart("bld_well", "models/village/Well.glb", Vector3(0f, 0f, -4.7f), scale = 0.6f)
        loadModelSmart("prop_hay", "models/village/Hay.glb", Vector3(4.5f, 0f, -5.0f), scale = 0.5f)
        loadModelSmart("prop_cart", "models/village/Cart.glb", Vector3(-3.2f, 0f, -5.5f), yaw = 70f, scale = 0.55f)

        // ---- 石板小路：田垄 → 广场 → 主街 ----
        loadModelSmart("prop_path_square", "models/village/Path_Square.glb", Vector3(0f, 0.02f, -4.3f), scale = 0.8f)
        loadModelSmart("prop_path_straight", "models/village/Path_Straight.glb", Vector3(0f, 0.02f, -5.1f), scale = 0.8f)
        loadModelSmart("prop_path_straight", "models/village/Path_Straight.glb", Vector3(0f, 0.02f, -5.9f), scale = 0.8f)
        loadModelSmart("prop_path_straight", "models/village/Path_Straight.glb", Vector3(0f, 0.02f, -6.7f), scale = 0.8f)

        // ---- 远处风车（地标）----
        loadModelSmart("bld_mill", "models/village/Mill.glb", Vector3(11.2f, 0f, -7.4f), yaw = -30f, scale = 0.5f)

        // ---- 田垄简易围栏（低矮木栏，几何体拼装更整齐） ----
        buildPlotFence()

        // ---- 花朵点缀（田边与屋前） ----
        loadModelSmart("prop_flower", "models/crops/Flower_4.glb", Vector3(-1.75f, 0f, -1.35f), scale = 0.32f)
        loadModelSmart("prop_flower", "models/crops/Flower_3.glb", Vector3(1.7f, 0f, -1.4f), scale = 0.3f)
        loadModelSmart("prop_flower", "models/crops/Flower_4.glb", Vector3(2.9f, 0f, -1.0f), scale = 0.28f)
        loadModelSmart("prop_flower", "models/crops/Flower_3.glb", Vector3(-3.3f, 0f, 0.35f), scale = 0.3f)
        loadModelSmart("prop_flower", "models/crops/Flower_1.glb", Vector3(0.2f, 0f, 0.7f), scale = 0.26f)
        loadModelSmart("prop_flower", "models/crops/Flower_4.glb", Vector3(-1.0f, 0f, 0.5f), scale = 0.3f)

        // ---- 生活器物（店门口/院子） ----
        loadModelSmart("prop_barrel", "models/village/Barrel.glb", Vector3(-1.35f, 0f, -5.75f), scale = 0.5f)
        loadModelSmart("prop_barrel", "models/village/Barrel.glb", Vector3(-1.75f, 0f, -5.95f), scale = 0.45f)
        loadModelSmart("prop_barrel", "models/village/Barrel.glb", Vector3(1.15f, 0f, -5.85f), scale = 0.5f)
        loadModelSmart("prop_crate", "models/village/Crate.glb", Vector3(3.55f, 0f, -5.7f), scale = 0.5f)
        loadModelSmart("prop_hay", "models/village/Hay.glb", Vector3(4.15f, 0f, -5.35f), scale = 0.42f)

        // ---- 小路延伸 ----
        loadModelSmart("prop_path_straight", "models/village/Path_Straight.glb", Vector3(-0.8f, 0.02f, -1.9f), yaw = 40f, scale = 0.8f)
        loadModelSmart("prop_path_straight", "models/village/Path_Straight.glb", Vector3(-2.2f, 0.02f, -1.5f), yaw = 75f, scale = 0.8f)
        loadModelSmart("prop_path_square", "models/village/Path_Square.glb", Vector3(1.6f, 0.02f, -1.6f), scale = 0.7f)

        // ---- 更多石头灌木 ----
        loadModelSmart("prop_rock", "models/nature/Rock_4.glb", Vector3(4.0f, 0f, 0.7f), scale = 0.8f)
        loadModelSmart("prop_rock", "models/nature/Rock_1.glb", Vector3(-3.1f, 0f, -8.4f), scale = 0.9f)
        loadModelSmart("prop_bush", "models/nature/Bush_1.glb", Vector3(5.0f, 0f, -0.2f), scale = 0.9f)
        loadModelSmart("prop_bush", "models/nature/Bush_2.glb", Vector3(-5.9f, 0f, -0.9f), scale = 0.9f)

        // ---- 街后树林补密 ----
        loadModelSmart("prop_tree_pine", "models/nature/PineTree_1.glb", Vector3(-4.6f, 0f, -9.0f), scale = 1.15f)
        loadModelSmart("prop_tree_pine", "models/nature/PineTree_2.glb", Vector3(1.7f, 0f, -9.3f), scale = 1.1f)
        loadModelSmart("prop_tree_pine", "models/nature/PineTree_3.glb", Vector3(5.1f, 0f, -9.0f), scale = 1.2f)

        // ---- 边缘树林与灌木（环绕氛围） ----
        loadSwaySmart("prop_tree_b", "models/nature/CommonTree_1.glb", Vector3(-11.6f, 0f, -3.8f), scale = 1.15f)
        loadSwaySmart("prop_tree_a", "models/nature/CommonTree_3.glb", Vector3(-12.0f, 0f, -0.6f), scale = 1.0f)
        loadSwaySmart("prop_tree_b", "models/nature/CommonTree_2.glb", Vector3(-6.2f, 0f, 4.8f), scale = 1.25f)
        loadSwaySmart("prop_tree_a", "models/nature/CommonTree_4.glb", Vector3(9.6f, 0f, 1.6f), scale = 1.1f)
        loadSwaySmart("prop_tree_b", "models/nature/CommonTree_5.glb", Vector3(9.6f, 0f, -5.4f), scale = 1.0f)
        loadSwaySmart("prop_tree_pine", "models/nature/PineTree_1.glb", Vector3(-2.6f, 0f, -13.6f), scale = 1.1f)
        loadSwaySmart("prop_tree_pine", "models/nature/PineTree_2.glb", Vector3(2.0f, 0f, -14.0f), scale = 1.2f)
        loadSwaySmart("prop_tree_pine", "models/nature/PineTree_3.glb", Vector3(7.4f, 0f, -13.4f), scale = 1.0f)
        loadModelSmart("prop_bush", "models/nature/Bush_1.glb", Vector3(-2.6f, 0f, 1.0f))
        loadModelSmart("prop_bush", "models/nature/Bush_2.glb", Vector3(2.8f, 0f, 0.6f))
        loadModelSmart("prop_rock", "models/nature/Rock_1.glb", Vector3(4.6f, 0f, -5.8f))
        loadModelSmart("prop_rock", "models/nature/Rock_2.glb", Vector3(-5.4f, 0f, -4.0f))
        loadModelSmart("prop_rock", "models/nature/Rock_3.glb", Vector3(1.6f, 0f, 0.9f))
    }

    // ---------------------------------------------------------------- 水车（独立轮子 + 旋转动画）

    private var wheelEntity: Entity? = null
    private var wheelTickerStarted = false

    /**
     * 水车轮子在 Tripo 模型里是独立连通块，已拆成 bld_watermill_wheel.glb
     * （几何以轴心为原点、轴沿本地 +Z）。挂到房身下，随父节点朝向；
     * 轮子下沉 0.12 个模型单位（×2.8 后约 0.34m）让下缘浸入湖水。
     */
    private suspend fun attachWaterWheel(house: Entity) {
        val uri = TripoOverrides.uriFor("bld_watermill_wheel") ?: return
        val wheel = runCatching { Entity.loadSuspend(uri) }.getOrNull() ?: return
        if (!valid) {
            wheel.destroy(true)
            return
        }
        wheel.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0.030f, 0.0516f, 0.3985f))
            setScaleVector(Vector3(1.55f, 1.55f, 1.55f))
            setQuaternion(Quat(Vector3(0f, 0f, 1f), 0f))
        }
        house.addChild(wheel)
        wheelEntity = wheel
        startWheelTicker()
    }

    private fun startWheelTicker() {
        if (wheelTickerStarted) return
        wheelTickerStarted = true
        ioScope.launch {
            var angle = 0f
            while (valid) {
                delay(33)
                val wheel = wheelEntity?.takeIf { it.valid } ?: continue
                angle -= 0.05f // ≈1.5 rad/s，与溪流方向一致（俯视顺时针）
                wheel.components[TransformComponent::class.java]
                    ?.setQuaternion(Quat(Vector3(0f, 0f, 1f), angle))
            }
        }
    }

    /** 摇曳注册表：树 (entity, 基准朝向, 相位) */
    private val swayables = mutableListOf<Triple<Entity, Float, Float>>()

    /** 加载一个带待机动画的 Tripo 角色（店主等静态 NPC） */
    private suspend fun loadIdleCharacter(
        itemId: String,
        position: Vector3,
        yaw: Float = 0f,
        scale: Float = 1f,
    ) {
        val uri = TripoOverrides.uriFor(itemId) ?: return
        val e = runCatching { Entity.loadSuspend(uri) }.getOrNull() ?: return
        if (!valid) {
            e.destroy(true)
            return
        }
        val s = scale * TripoOverrides.scaleFor(itemId)
        e.components[TransformComponent::class.java]?.apply {
            setPosition(
                Vector3(position.x, position.y + TripoOverrides.offsetYFor(itemId) * s, position.z)
            )
            setEulerAngles(EulerAngles(0f, yaw + TripoOverrides.yawFor(itemId), 0f))
            setScaleVector(Vector3(s, s, s))
        }
        addChild(e)
        runCatching {
            val idle = e.getAnimationResources()
                .firstOrNull { it.getName().contains("idle", true) }
                ?.repeat(-1)
            if (idle != null) {
                e.playAnimation(
                    idle,
                    AnimationPlayConfig(0.2f, AnimationTransitionMode.CROSSFADE, 0, 1f),
                )
            }
        }
    }

    /** 仅覆盖加载：只有存在 Tripo 覆盖时才生成（新地标用），返回根实体 */
    private suspend fun loadOverrideOnly(
        itemId: String,
        position: Vector3,
        yaw: Float = 0f,
        scale: Float = 1f,
    ): Entity? {
        val overrideUri = TripoOverrides.uriFor(itemId) ?: return null
        val e = runCatching { Entity.loadSuspend(overrideUri) }.getOrNull() ?: return null
        if (!valid) {
            e.destroy(true)
            return null
        }
        val s2 = scale * TripoOverrides.scaleFor(itemId)
        val y = yaw + TripoOverrides.yawFor(itemId)
        e.components[TransformComponent::class.java]?.apply {
            setPosition(
                Vector3(position.x, position.y + TripoOverrides.offsetYFor(itemId) * s2, position.z)
            )
            setEulerAngles(EulerAngles(0f, y, 0f))
            setScaleVector(Vector3(s2, s2, s2))
        }
        addChild(e)
        return e
    }

    /** 智能加载：优先使用 Tripo 覆盖模型（models/tripo/<itemId>.glb），失败回退默认模型 */
    private suspend fun loadModelSmart(
        itemId: String,
        defaultUri: String,
        position: Vector3,
        yaw: Float = 0f,
        scale: Float = 1f,
    ) {
        val overrideUri = TripoOverrides.uriFor(itemId)
        if (overrideUri != null) {
            val e = runCatching { Entity.loadSuspend(overrideUri) }.getOrNull()
            if (e != null && valid) {
                val s = scale * TripoOverrides.scaleFor(itemId)
                val y = yaw + TripoOverrides.yawFor(itemId)
                e.components[TransformComponent::class.java]?.apply {
                    setPosition(
                        Vector3(position.x, position.y + TripoOverrides.offsetYFor(itemId) * s, position.z)
                    )
                    setEulerAngles(EulerAngles(0f, y, 0f))
                    setScaleVector(Vector3(s, s, s))
                }
                addChild(e)
                return
            }
            e?.destroy(true)
        }
        loadModel(defaultUri, position, yaw, scale)
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
    ): Entity? {
        val entity = Entity.loadSuspend("asset://$uri")
        if (!valid) {
            entity.destroy(true)
            return null
        }
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(position)
            setEulerAngles(EulerAngles(0f, yaw, 0f))
            setScaleVector(Vector3(scale, scale, scale))
        }
        addChild(entity)
        return entity
    }

    /** 会摇摆的树：优先 Tripo 覆盖（prop_tree_a/b/pine），否则回退默认美术 */
    private suspend fun loadSwaySmart(
        itemId: String,
        defaultUri: String,
        position: Vector3,
        scale: Float = 1f,
    ) {
        val overrideUri = TripoOverrides.uriFor(itemId)
        var e: Entity? = null
        if (overrideUri != null) {
            val ent = runCatching { Entity.loadSuspend(overrideUri) }.getOrNull()
            if (ent != null && valid) {
                val s = scale * TripoOverrides.scaleFor(itemId)
                ent.components[TransformComponent::class.java]?.apply {
                    setPosition(
                        Vector3(position.x, position.y + TripoOverrides.offsetYFor(itemId) * s, position.z)
                    )
                    setEulerAngles(EulerAngles(0f, TripoOverrides.yawFor(itemId), 0f))
                    setScaleVector(Vector3(s, s, s))
                }
                addChild(ent)
                e = ent
            } else {
                ent?.destroy(true)
            }
        }
        if (e == null) {
            e = loadModel(defaultUri, position, scale = scale)
        }
        val tree = e ?: return
        swayables.add(Triple(tree, TripoOverrides.yawFor(itemId), (0..62).random() / 10f))
        startSwayTickerIfNeeded()
    }

    /** 加载一棵会随风轻摆的树 */
    private suspend fun loadSway(uri: String, position: Vector3, scale: Float = 1f) {
        val e = loadModel(uri, position, scale = scale) ?: return
        swayables.add(Triple(e, 0f, (0..62).random() / 10f))
        startSwayTickerIfNeeded()
    }

    private var swayTickerStarted = false

    private fun startSwayTickerIfNeeded() {
        if (swayTickerStarted) return
        swayTickerStarted = true
        ioScope.launch {
            var t = 0f
            while (valid) {
                t += 0.08f
                swayables.forEach { (e, baseYaw, phase) ->
                    if (!e.valid) return@forEach
                    val sway = kotlin.math.sin(t * 0.9f + phase) * 2.2f
                    val rock = kotlin.math.sin(t * 0.6f + phase * 1.7f) * 1.2f
                    e.components[TransformComponent::class.java]
                        ?.setEulerAngles(EulerAngles(rock, baseYaw + sway, 0f))
                }
                kotlinx.coroutines.delay(130)
            }
        }
    }

    private companion object {
        // ---- 尺寸与布局常量（米） ----
        const val MAX_PLOTS = 8
        const val PLOT_GAP = 1.9f
        const val PLOT_ROW_Z0 = -2.0f
        const val ROW_GAP = 1.35f
        const val DAY_PERIOD = 480f   // 一昼夜 8 分钟（演示尺度）
        val COL_XS = floatArrayOf(-2.85f, -0.95f, 0.95f, 2.85f)

        // ---- 粉彩调色板（保留几何体用） ----
        val SOIL = Color4(0.60f, 0.45f, 0.34f, 1f)       // 田垄棕
        val WOOD = Color4(0.87f, 0.74f, 0.58f, 1f)       // 原木
        val WOOD_DARK = Color4(0.55f, 0.42f, 0.30f, 1f)  // 深木
        val PLANK = Color4(0.82f, 0.69f, 0.52f, 1f)      // 栈木板
    }
}
