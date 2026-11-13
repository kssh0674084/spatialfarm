package com.spatialfarm.game.scene.props

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3

/** 店铺类型：点房子打开对应功能窗口 */
enum class ShopKind { SEED, BUYER, TOOL }

/**
 * 店铺交互点：罩在 3D 房子上的隐形碰撞盒。
 * 点房子 = 打开该店的功能（买种/卖菜/工具）。
 */
class ShopPointEntity(
    private val kind: ShopKind,
    private val size: Vector3,
) : Entity() {
    init {
        setName(
            when (kind) {
                ShopKind.SEED -> "shop_point_seed"
                ShopKind.BUYER -> "shop_point_buyer"
                ShopKind.TOOL -> "shop_point_tool"
            }
        )
        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(size)),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
    }

    fun shopKind(): ShopKind = kind
}
