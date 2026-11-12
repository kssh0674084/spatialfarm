package com.spatialfarm.game.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BoxOption
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/**
 * 村口小院 M2-1 地基骨架（旷野之息风格）。
 *
 * Stage 坐标系：原点在用户脚部，+X 右、+Y 上、+Z 朝向用户。
 * 布局：
 *   - 地形：RawMesh 程序化起伏草原（见 [TerrainMesh]），村庄功能区已压平
 *   - 玩家小院（中前）：2 块田垄 + 篱笆 + 木牌
 *   - 玩家小屋（左后）：奶白墙 + 珊瑚红屋顶
 *   - 小镇主街（正北远处）：三间店铺占位（梅姨杂货/胖婶食堂/锤叔作坊）
 *   - 码头（右侧）：木栈板占位（海叔的船 M3 再来）
 */
class VillageEntity : Entity() {
    /** 场景级材质注册表：同一颜色只创建一次材质（资源复用规范）。
     *  注意：必须声明在 init 之前（Kotlin 按声明顺序初始化）。 */
    private val materials = mutableMapOf<String, Material>()

    init {
        setUp()
    }

    private fun mat(key: String, color: Color4): Material =
        materials.getOrPut(key) {
            UnlitMaterial.create().apply { setBaseColor(color) }
        }

    private fun setUp() {
        setName("village_root")
        buildTerrain()
        buildFarmPlots()
        buildFenceAndSign()
        buildPlayerHouse()
        buildMainStreet()
        buildDock()
    }

    // ---------------------------------------------------------------- 草原地形

    private fun buildTerrain() {
        val terrain = ModelEntity(
            TerrainMesh.create(),
            mat("terrain_base", Color4(1f, 1f, 1f, 1f)) // 白色基底 × 顶点色
        )
        terrain.setName("meadow_terrain")
        terrain.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0f, 0f))
        addChild(terrain)
    }

    // ---------------------------------------------------------------- 玩家田垄（2 块初始田）

    private fun buildFarmPlots() {
        val plots = Entity()
        plots.setName("farm_plots")
        plots.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0f, PLOT_Z))

        val soilMesh = MeshResource.createBox(
            BoxOption(Vector3(PLOT_W, PLOT_T, PLOT_D)).setCornerRadius(0.12f).setName("plot_soil")
        )
        val soilMat = mat("soil", SOIL)

        for (i in 0 until 2) {
            val x = -PLOT_GAP / 2f + i * PLOT_GAP
            val soil = ModelEntity(soilMesh, soilMat)
            soil.setName("plot_$i")
            soil.components[TransformComponent::class.java]!!.apply {
                setPosition(Vector3(x, PLOT_T / 2f, 0f))
            }
            plots.addChild(soil)
        }
        addChild(plots)
    }

    // ---------------------------------------------------------------- 篱笆与木牌

    private fun buildFenceAndSign() {
        // 田垄四周的低矮篱笆桩
        val fence = Entity()
        fence.setName("fence")
        fence.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0f, PLOT_Z))

        val postMesh = MeshResource.createBox(
            BoxOption(Vector3(0.1f, FENCE_H, 0.1f)).setCornerRadius(0.03f).setName("fence_post")
        )
        val postMat = mat("wood", WOOD)
        val halfW = PLOT_GAP / 2f + PLOT_W / 2f + 0.25f
        val halfD = PLOT_D / 2f + 0.25f

        var n = 0
        fun post(x: Float, z: Float) {
            val p = ModelEntity(postMesh, postMat)
            p.setName("fence_post_$n")
            p.components[TransformComponent::class.java]!!.setPosition(Vector3(x, FENCE_H / 2f, z))
            fence.addChild(p)
            n++
        }
        for (i in 0..4) {
            val x = -halfW + (2 * halfW) * i / 4f
            post(x, -halfD)
            post(x, halfD)
        }
        post(-halfW, 0f)
        post(halfW, 0f)
        addChild(fence)

        // 迎宾木牌：木柱 + 黄色牌面
        val sign = Entity()
        sign.setName("welcome_sign")
        sign.components[TransformComponent::class.java]!!.setPosition(Vector3(SIGN_X, 0f, PLOT_Z))

        val pole = ModelEntity(
            MeshResource.createCylinder(1.1f, 0.06f),
            mat("wood", WOOD)
        )
        pole.setName("sign_pole")
        pole.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.55f, 0f))
        sign.addChild(pole)

        val board = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(0.9f, 0.5f, 0.08f)).setCornerRadius(0.08f).setName("sign_board")
            ),
            mat("butter", BUTTER)
        )
        board.setName("sign_board_face")
        board.components[TransformComponent::class.java]!!.apply {
            setPosition(Vector3(0f, 1.05f, 0f))
            setEulerAngles(EulerAngles(0f, SIGN_BOARD_YAW, 0f))
        }
        sign.addChild(board)
        addChild(sign)
    }

    // ---------------------------------------------------------------- 玩家小屋

    private fun buildPlayerHouse() {
        val house = Entity()
        house.setName("player_house")
        house.components[TransformComponent::class.java]!!
            .setPosition(Vector3(HOUSE_X, 0f, HOUSE_Z))

        val wall = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(2.2f, 1.7f, 1.8f)).setCornerRadius(0.06f).setName("house_wall")
            ),
            mat("cream", CREAM)
        )
        wall.setName("house_wall_body")
        wall.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.85f, 0f))
        house.addChild(wall)

        val roof = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(2.5f, 0.55f, 2.1f)).setCornerRadius(0.1f).setName("house_roof")
            ),
            mat("coral", CORAL)
        )
        roof.setName("house_roof_top")
        roof.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 1.98f, 0f))
        house.addChild(roof)

        val door = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(0.55f, 0.95f, 0.06f)).setCornerRadius(0.04f).setName("house_door")
            ),
            mat("wood_dark", WOOD_DARK)
        )
        door.setName("house_door_face")
        door.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.48f, 0.92f))
        house.addChild(door)
        addChild(house)
    }

    // ---------------------------------------------------------------- 小镇主街（三间店铺占位）

    private fun buildMainStreet() {
        val street = Entity()
        street.setName("main_street")
        street.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0f, STREET_Z))

        val shopMesh = MeshResource.createBox(
            BoxOption(Vector3(1.7f, 1.4f, 1.5f)).setCornerRadius(0.06f).setName("shop_body")
        )
        val awningMesh = MeshResource.createBox(
            BoxOption(Vector3(1.9f, 0.18f, 0.5f)).setCornerRadius(0.08f).setName("shop_awning")
        )

        val shops = listOf(
            Triple("shop_grocery", PINK, Vector3(-2.6f, 0f, 0f)),
            Triple("shop_canteen", MINT, Vector3(0f, 0f, -0.4f)),
            Triple("shop_workshop", LAVENDER, Vector3(2.6f, 0f, 0f)),
        )
        for ((name, color, pos) in shops) {
            val shop = Entity()
            shop.setName(name)
            shop.components[TransformComponent::class.java]!!.setPosition(pos)

            val body = ModelEntity(shopMesh, mat("shop_${name}", color))
            body.setName("${name}_body")
            body.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 0.7f, 0f))
            shop.addChild(body)

            val awning = ModelEntity(awningMesh, mat("awning_${name}", BUTTER))
            awning.setName("${name}_awning")
            awning.components[TransformComponent::class.java]!!
                .setPosition(Vector3(0f, 1.5f, 0.75f))
            shop.addChild(awning)
            street.addChild(shop)
        }
        addChild(street)
    }

    // ---------------------------------------------------------------- 码头（木栈板占位）

    private fun buildDock() {
        val dock = Entity()
        dock.setName("dock")
        dock.components[TransformComponent::class.java]!!.setPosition(Vector3(DOCK_X, 0f, DOCK_Z))

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

    private companion object {
        // ---- 尺寸与布局常量（米） ----
        const val PLOT_W = 1.3f
        const val PLOT_T = 0.22f
        const val PLOT_D = 0.9f
        const val PLOT_GAP = 2.0f
        const val PLOT_Z = -2.4f

        const val FENCE_H = 0.45f
        const val SIGN_X = 2.3f
        const val SIGN_BOARD_YAW = -18f

        const val HOUSE_X = -4.2f
        const val HOUSE_Z = -1.2f

        const val STREET_Z = -6.2f

        const val DOCK_X = 5.6f
        const val DOCK_Z = -2.2f

        // ---- 粉彩调色板 ----
        val SOIL = Color4(0.60f, 0.45f, 0.34f, 1f)       // 田垄棕
        val WOOD = Color4(0.87f, 0.74f, 0.58f, 1f)       // 原木
        val WOOD_DARK = Color4(0.62f, 0.48f, 0.36f, 1f)  // 深木
        val BUTTER = Color4(0.99f, 0.93f, 0.72f, 1f)     // 奶油黄
        val CREAM = Color4(0.98f, 0.95f, 0.88f, 1f)      // 奶白墙
        val CORAL = Color4(0.94f, 0.63f, 0.60f, 1f)      // 珊瑚红屋顶
        val PINK = Color4(0.96f, 0.80f, 0.82f, 1f)       // 梅姨杂货
        val MINT = Color4(0.78f, 0.93f, 0.85f, 1f)       // 胖婶食堂
        val LAVENDER = Color4(0.85f, 0.82f, 0.95f, 1f)   // 锤叔作坊
        val PLANK = Color4(0.82f, 0.69f, 0.52f, 1f)      // 栈木板
    }
}
