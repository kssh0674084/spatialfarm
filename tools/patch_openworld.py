# -*- coding: utf-8 -*-
"""一次性补丁：VillageScene 开放世界改造（海洋/湖泊/溪流/远山/散布/移动循环）"""
import io

p = 'app/src/main/java/com/spatialfarm/game/scene/VillageScene.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 天空半径 55 -> 520
s = s.replace(
    'MeshResource.createSphere(SphereOption(55f).setName("sky_sphere_$name"))',
    'MeshResource.createSphere(SphereOption(520f).setName("sky_sphere_$name"))',
)

# 2) setUp 增加：海/湖/溪/远山/散布 + 移动循环
s = s.replace(
    """        buildAmbience()
        spawnWildlife()
        startBgm()""",
    """        buildAmbience()
        spawnWildlife()
        buildSeaAndLakes()
        buildMountains()
        scatterWildIsland()
        startBgm()
        Locomotion.start()
        ioScope.launch { locomotionLoop() }""",
)

OPENWORLD = """    // ---------------------------------------------------------------- 环海 / 湖泊 / 溪流

    private fun waterMat(key: String, color: Color4): UnlitMaterial =
        UnlitMaterial.create().apply {
            setBaseColor(color)
            toGlobal()
        }.also { materials.getOrPut(key) { it } }

    private fun buildSeaAndLakes() {
        val seaMat = waterMat("sea", Color4(0.40f, 0.77f, 0.90f, 1f))
        val lakeMat = waterMat("lake", Color4(0.44f, 0.80f, 0.93f, 1f))

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
        foam.components[TransformComponent::class.java]!!
            .setPosition(Vector3(0f, TerrainMesh.SEA_LEVEL + 0.02f, 0f))
        addChild(foam)

        val lake = ModelEntity(
            MeshResource.createBox(BoxOption(Vector3(24f, 0.05f, 22f)).setName("lake")),
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
    }

    // ---------------------------------------------------------------- 远山天际线

    private fun buildMountains() {
        val rockMat = UnlitMaterial.create().apply {
            setBaseColor(Color4(0.55f, 0.62f, 0.76f, 1f))
            toGlobal()
        }
        val snowMat = UnlitMaterial.create().apply {
            setBaseColor(Color4(0.96f, 0.97f, 1f, 1f))
            toGlobal()
        }
        val rnd = kotlin.random.Random(7)
        for (i in 0 until 10) {
            val angle = (i / 10f) * Math.PI.toFloat() * 2f + rnd.nextFloat() * 0.35f
            val dist = 165f + rnd.nextFloat() * 70f
            val h = 42f + rnd.nextFloat() * 45f
            val r = h * (0.62f + rnd.nextFloat() * 0.25f)
            val x = kotlin.math.cos(angle) * dist
            val z = kotlin.math.sin(angle) * dist

            val mountain = Entity()
            mountain.setName("mountain_$i")
            val body = ModelEntity(MeshResource.createCone(h, r), rockMat)
            body.setName("mountain_body")
            body.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, h / 2f, 0f))
            mountain.addChild(body)

            val capH = h * 0.30f
            val cap = ModelEntity(MeshResource.createCone(capH, r * 0.34f), snowMat)
            cap.setName("mountain_snow")
            cap.components[TransformComponent::class.java]!!
                .setPosition(Vector3(0f, h - capH / 2f, 0f))
            mountain.addChild(cap)

            mountain.components[TransformComponent::class.java]!!
                .setPosition(Vector3(x, TerrainMesh.SEA_LEVEL, z))
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
            repeat(26) {
                val pnt = randPoint()
                val fa = flowerAssets[rnd.nextInt(flowerAssets.size)]
                loadModelSmart(
                    "prop_flower",
                    "models/crops/$fa.glb",
                    Vector3(pnt[0], TerrainMesh.height(pnt[0], pnt[1]), pnt[1]),
                    yaw = rnd.nextFloat() * 360f,
                    scale = 0.3f + rnd.nextFloat() * 0.18f,
                )
            }

            repeat(18) {
                val pnt = randPoint()
                val isPine = rnd.nextBoolean()
                loadSway(
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

    // ---------------------------------------------------------------- 世界生命（飞鸟/蝴蝶/炊烟）"""

anchor = "    // ---------------------------------------------------------------- 世界生命（飞鸟/蝴蝶/炊烟）"
assert anchor in s, 'anchor not found'
s = s.replace(anchor, OPENWORLD, 1)

# 3) applyDioramaScale 改为统一走 applyRootTransform
old_diorama = """    private fun applyDioramaScale(s: Float) {
        scaleCurrent = s
        components[TransformComponent::class.java]?.apply {
            setScaleVector(Vector3(s, s, s))
            setPosition(Vector3(0f, 1.05f * (1f - s), 0f))
        }
        skyEntity?.components?.get(TransformComponent::class.java)?.apply {
            val inv = 1f / s
            setScaleVector(Vector3(inv, inv, inv))
            setPosition(Vector3(0f, -2f * inv, 0f))
        }
    }"""
new_diorama = """    private fun applyDioramaScale(s: Float) {
        scaleCurrent = s
        applyRootTransform(Vector3(-Locomotion.playerX, -Locomotion.groundHeight, -Locomotion.playerZ))
        skyEntity?.components?.get(TransformComponent::class.java)?.apply {
            val inv = 1f / s
            setScaleVector(Vector3(inv, inv, inv))
            setPosition(Vector3(0f, -2f * inv, 0f))
        }
    }"""
if old_diorama in s:
    s = s.replace(old_diorama, new_diorama)
    print('diorama merged into root transform')
else:
    print('WARN: diorama block not matched')

io.open(p, 'w', encoding='utf-8').write(s)
print('VillageScene open-world patch applied')
