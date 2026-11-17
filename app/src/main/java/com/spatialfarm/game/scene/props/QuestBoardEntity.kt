package com.spatialfarm.game.scene.props

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
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialfarm.game.data.TripoOverrides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 广场委托板（3D 可点按实体）：点按打开委托窗口。
 *
 * 优先使用参考图生成的 `bld_questboard` 模型；缺失时回退到几何体拼装。
 */
class QuestBoardEntity : Entity() {
    init {
        setName("quest_board")

        val override = TripoOverrides.uriFor("bld_questboard")
        if (override != null) {
            loadOverrideModel(override)
        } else {
            buildFallback()
        }

        components[CollisionComponent::class.java] =
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(2.6f, 1.9f, 1.4f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        components[InteractableComponent::class.java] = InteractableComponent()
        components[HoverEffectComponent::class.java] = HoverEffectComponent()
    }

    private fun loadOverrideModel(uri: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val model = runCatching { Entity.loadSuspend(uri) }.getOrNull() ?: return@launch
            if (!valid) {
                model.destroy(true)
                return@launch
            }
            val s = TripoOverrides.scaleFor("bld_questboard")
            model.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, TripoOverrides.offsetYFor("bld_questboard") * s, 0f))
                setScaleVector(Vector3(s, s, s))
                setEulerAngles(EulerAngles(0f, TripoOverrides.yawFor("bld_questboard"), 0f))
            }
            addChild(model)
        }
    }

    private fun buildFallback() {
        val woodMat = UnlitMaterial.create().apply { setBaseColor(Color4(0.62f, 0.48f, 0.36f, 1f)) }
        val paperMat = UnlitMaterial.create().apply { setBaseColor(Color4(0.99f, 0.95f, 0.82f, 1f)) }

        // 两根立柱
        val postMesh = MeshResource.createBox(
            BoxOption(Vector3(0.09f, 1.1f, 0.09f)).setCornerRadius(0.02f).setName("qb_post")
        )
        for (x in listOf(-0.5f, 0.5f)) {
            val post = ModelEntity(postMesh, woodMat)
            post.setName("qb_post_$x")
            post.components[TransformComponent::class.java]!!.setPosition(Vector3(x, 0.55f, 0f))
            addChild(post)
        }

        // 板面
        val board = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(1.25f, 0.75f, 0.06f)).setCornerRadius(0.05f).setName("qb_board")
            ),
            paperMat
        )
        board.setName("qb_board_face")
        board.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 1.05f, 0f))
        addChild(board)

        // 顶部小旗檐
        val awning = ModelEntity(
            MeshResource.createBox(
                BoxOption(Vector3(1.4f, 0.12f, 0.3f)).setCornerRadius(0.05f).setName("qb_awning")
            ),
            UnlitMaterial.create().apply { setBaseColor(Color4(0.94f, 0.63f, 0.60f, 1f)) }
        )
        awning.setName("qb_awning_top")
        awning.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, 1.5f, 0.1f))
        addChild(awning)
    }
}
