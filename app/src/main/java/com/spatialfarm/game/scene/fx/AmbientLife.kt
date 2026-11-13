package com.spatialfarm.game.scene.fx

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BoxOption
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 环境生命：让世界"动起来"。
 * - 鸟群：2-3 只小鸟绕岛飞行（翅膀扇动）
 * - 蝴蝶：田边绕圈飞舞
 * - 炊烟：烟囱定时冒烟（上升、放大、消散）
 */
object AmbientLife {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val wingMesh: MeshResource by lazy {
        MeshResource.createBox(
            BoxOption(Vector3(0.16f, 0.01f, 0.09f)).setCornerRadius(0.02f).setName("fx_wing")
        ).also { it.toGlobal() }
    }
    private val puffMesh: MeshResource by lazy {
        MeshResource.createSphere(SphereOption(0.07f)).also { it.toGlobal() }
    }

    /** 小鸟（两只翅膀 + 身体），沿水平圆环飞行并扇翅。 */
    fun spawnBird(
        parent: Entity,
        center: Vector3,
        radius: Float,
        height: Float,
        speed: Float,
        phase: Float,
        wingColor: Color4,
    ): Entity {
        val bird = Entity()
        bird.setName("bird")
        val mat = UnlitMaterial.create().apply {
            setBaseColor(wingColor)
            toGlobal()
        }
        val body = ModelEntity(MeshResource.createSphere(SphereOption(0.045f)).also { it.toGlobal() }, mat)
        body.setName("bird_body")
        bird.addChild(body)
        val left = ModelEntity(wingMesh, mat)
        left.setName("wing_l")
        left.components[TransformComponent::class.java]!!.setPosition(Vector3(-0.08f, 0f, 0f))
        bird.addChild(left)
        val right = ModelEntity(wingMesh, mat)
        right.setName("wing_r")
        right.components[TransformComponent::class.java]!!.setPosition(Vector3(0.08f, 0f, 0f))
        bird.addChild(right)
        parent.addChild(bird)

        scope.launch {
            var t = phase
            while (bird.valid) {
                t += 0.06f
                val a = t * speed
                bird.components[TransformComponent::class.java]?.apply {
                    setPosition(
                        Vector3(
                            center.x + cos(a) * radius,
                            height + sin(t * 1.7f) * 0.35f,
                            center.z + sin(a) * radius,
                        )
                    )
                    setEulerAngles(EulerAngles(0f, (-a * 57.3f) + 90f, 0f))
                }
                val flap = sin(t * 14f) * 38f
                left.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, flap))
                right.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, -flap))
                delay(110)
            }
        }
        return bird
    }

    /** 蝴蝶：在田边小幅绕圈。 */
    fun spawnButterfly(parent: Entity, center: Vector3, color: Color4): Entity {
        val fly = Entity()
        fly.setName("butterfly")
        val mat = UnlitMaterial.create().apply {
            setBaseColor(color)
            toGlobal()
        }
        val l = ModelEntity(wingMesh, mat)
        l.setName("bwing_l")
        l.components[TransformComponent::class.java]!!.setScaleVector(Vector3(0.5f, 0.5f, 0.5f))
        fly.addChild(l)
        val r = ModelEntity(wingMesh, mat)
        r.setName("bwing_r")
        r.components[TransformComponent::class.java]!!.setScaleVector(Vector3(0.5f, 0.5f, 0.5f))
        fly.addChild(r)
        parent.addChild(fly)

        scope.launch {
            var t = (0..100).random() * 0.1f
            while (fly.valid) {
                t += 0.09f
                val a = t * 0.9f
                fly.components[TransformComponent::class.java]?.setPosition(
                    Vector3(
                        center.x + cos(a) * 0.85f,
                        center.y + 0.25f + sin(t * 2.3f) * 0.12f,
                        center.z + sin(a) * 0.55f,
                    )
                )
                val flap = sin(t * 22f) * 45f
                l.components[TransformComponent::class.java]
                    ?.setEulerAngles(EulerAngles(0f, 0f, flap))
                r.components[TransformComponent::class.java]
                    ?.setEulerAngles(EulerAngles(0f, 0f, -flap))
                delay(90)
            }
        }
        return fly
    }

    /** 烟囱持续冒烟：上升 + 放大 + 消散。 */
    fun chimneySmoke(parent: Entity, chimney: Vector3) {
        val mat = UnlitMaterial.create().apply {
            setBaseColor(Color4(0.86f, 0.86f, 0.84f, 1f))
            toGlobal()
        }
        scope.launch {
            while (parent.valid) {
                val puff = ModelEntity(puffMesh, mat)
                puff.setName("smoke")
                puff.components[TransformComponent::class.java]?.setPosition(chimney)
                parent.addChild(puff)
                launch {
                    val steps = 46
                    for (i in 1..steps) {
                        if (!puff.valid) return@launch
                        val t = i / steps.toFloat()
                        puff.components[TransformComponent::class.java]?.apply {
                            setPosition(Vector3(chimney.x + t * 0.25f, chimney.y + t * 1.4f, chimney.z))
                            val k = 0.8f + t * 1.6f
                            setScaleVector(Vector3(k, k, k))
                        }
                        delay(90)
                    }
                    if (puff.valid) puff.destroy(true)
                }
                delay(3200)
            }
        }
    }
}
