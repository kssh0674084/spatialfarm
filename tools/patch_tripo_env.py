# -*- coding: utf-8 -*-
"""Tripo 第一批环境资产接入：湖边水车小屋 / 前景大树 / 花丛 A-B"""
import io

p = 'app/src/main/java/com/spatialfarm/game/scene/VillageScene.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 新增：仅覆盖模型加载（无默认模型的地标用）
if 'loadOverrideOnly' not in s:
    s = s.replace(
        """    /** 智能加载：优先使用 Tripo 覆盖模型（models/tripo/<itemId>.glb），失败回退默认模型 */""",
        """    /** 仅覆盖加载：只有存在 Tripo 覆盖时才生成（新地标用） */
    private suspend fun loadOverrideOnly(
        itemId: String,
        position: Vector3,
        yaw: Float = 0f,
        scale: Float = 1f,
    ) {
        val overrideUri = TripoOverrides.uriFor(itemId) ?: return
        val e = runCatching { Entity.loadSuspend(overrideUri) }.getOrNull() ?: return
        if (!valid) {
            e.destroy(true)
            return
        }
        val s2 = scale * TripoOverrides.scaleFor(itemId)
        val y = yaw + TripoOverrides.yawFor(itemId)
        e.components[TransformComponent::class.java]?.apply {
            setPosition(position)
            setEulerAngles(EulerAngles(0f, y, 0f))
            setScaleVector(Vector3(s2, s2, s2))
        }
        addChild(e)
    }

    /** 智能加载：优先使用 Tripo 覆盖模型（models/tripo/<itemId>.glb），失败回退默认模型 */""",
    )

# 2) 在 loadModels 里加：水车小屋（湖边）+ 大树（近景/湖边）
if 'bld_watermill' not in s:
    anchor = '        // ---- 主街店铺（北）----'
    assert anchor in s
    s = s.replace(
        anchor,
        """        // ---- Tripo 新地标：湖边水车小屋 ----
        run {
            val wx = -12.7f
            val wz = 9.2f
            loadOverrideOnly(
                "bld_watermill",
                Vector3(wx, TerrainMesh.height(wx, wz), wz),
                yaw = 140f,
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

        // ---- 主街店铺（北） ----""",
        1,
    )

# 3) 野花散布：交替使用 prop_flower_a / prop_flower_b 覆盖
old_flower = """            val flowerAssets = listOf("Flower_1", "Flower_2", "Flower_3", "Flower_4")
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
            }"""
new_flower = """            val flowerAssets = listOf("Flower_1", "Flower_2", "Flower_3", "Flower_4")
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
            }"""
assert old_flower in s, 'flower block not found'
s = s.replace(old_flower, new_flower)

io.open(p, 'w', encoding='utf-8').write(s)
print('Tripo env integration patched')

# 4) manifest 初始条目（校准用，后续按截图调）
m = 'app/src/main/assets/models/tripo/manifest.json'
io.open(m, 'w', encoding='utf-8').write('''{
  "_comment": "Tripo 模型校准表：scale=整体缩放, yaw=附加朝向角度（度）",
  "bld_watermill": { "scale": 1.0, "yaw": 0 },
  "prop_tree_big": { "scale": 1.0, "yaw": 0 },
  "prop_flower_a": { "scale": 1.0, "yaw": 0 },
  "prop_flower_b": { "scale": 1.0, "yaw": 0 }
}
''')
print('manifest initialized')
