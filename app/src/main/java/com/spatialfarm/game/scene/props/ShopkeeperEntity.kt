package com.spatialfarm.game.scene.props

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationTransitionMode
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.TripoOverrides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 店主 NPC：加载 Tripo 角色 + 循环待机动画，可点击直接打开对应店铺。
 *
 * @param itemId  Tripo 资产 id（mei_yi / pang_shen / chui_shu）
 * @param kind    店铺类型（决定点按打开哪个窗口）
 */
class ShopkeeperEntity(
    private val itemId: String,
    private val kind: ShopKind,
    private val yaw: Float = -90f,
    private val scale: Float = 1f,
) : Entity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        setName("shopkeeper_$itemId")
        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(0.5f, 1.6f, 0.5f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
        loadBody()
    }

    fun shopKind(): ShopKind = kind

    private fun loadBody() {
        val uri = TripoOverrides.uriFor(itemId) ?: return
        scope.launch {
            val model = runCatching { Entity.loadSuspend(uri) }.getOrNull() ?: return@launch
            if (!valid) {
                model.destroy(true)
                return@launch
            }
            val s = scale * TripoOverrides.scaleFor(itemId)
            model.components[TransformComponent::class.java]?.apply {
                setPosition(
                    Vector3(0f, TripoOverrides.offsetYFor(itemId) * s, 0f)
                )
                setEulerAngles(EulerAngles(0f, yaw + TripoOverrides.yawFor(itemId), 0f))
                setScaleVector(Vector3(s, s, s))
            }
            addChild(model)
            runCatching {
                val idle = model.getAnimationResources()
                    .firstOrNull { it.getName().contains("idle", true) }
                    ?.repeat(-1)
                if (idle != null) {
                    model.playAnimation(
                        idle,
                        AnimationPlayConfig(0.2f, AnimationTransitionMode.CROSSFADE, 0, 1f),
                    )
                }
            }
        }
    }
}
