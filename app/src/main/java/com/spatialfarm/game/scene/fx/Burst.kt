package com.spatialfarm.game.scene.fx

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 轻量粒子特效（不依赖 ParticleComponent）：
 * 用小球体实体做短暂爆发动画，播完即销毁——给每个操作一个「看得见的反馈」。
 */
object Burst {
    private val scope = CoroutineScope(Dispatchers.Main)

    private var sphereMesh: MeshResource? = null
    private var waterMat: UnlitMaterial? = null
    private var sparkMat: UnlitMaterial? = null

    private fun mesh(): MeshResource =
        sphereMesh ?: MeshResource.createSphere(SphereOption(0.035f)).also { sphereMesh = it }

    private fun waterMaterial(): UnlitMaterial =
        waterMat ?: UnlitMaterial.create().apply {
            setBaseColor(Color4(0.55f, 0.80f, 0.98f, 1f))
        }.also { waterMat = it }

    private fun sparkMaterial(): UnlitMaterial =
        sparkMat ?: UnlitMaterial.create().apply {
            setBaseColor(Color4(1f, 0.85f, 0.35f, 1f))
        }.also { sparkMat = it }

    /** 浇水水花：蓝色小球向上四散 */
    fun splash(parent: Entity, at: Vector3) {
        burst(parent, at, waterMaterial(), count = 7, speed = 0.35f, up = 0.9f)
    }

    /** 收获/生长闪光：金色小球向上飘散 */
    fun sparkle(parent: Entity, at: Vector3) {
        burst(parent, at, sparkMaterial(), count = 9, speed = 0.28f, up = 1.2f)
    }

    private fun burst(
        parent: Entity,
        at: Vector3,
        material: UnlitMaterial,
        count: Int,
        speed: Float,
        up: Float,
    ) {
        val particles = mutableListOf<Entity>()
        for (i in 0 until count) {
            val p = ModelEntity(mesh(), material)
            p.setName("fx_$i")
            p.components[TransformComponent::class.java]?.setPosition(at)
            parent.addChild(p)
            particles.add(p)
        }
        val dirs = particles.map {
            val a = Random.nextFloat() * 6.283f
            Vector3(cos(a) * speed * Random.nextFloat(), up * (0.5f + Random.nextFloat()), sin(a) * speed * Random.nextFloat())
        }
        scope.launch {
            val steps = 14
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                particles.forEachIndexed { i, p ->
                    if (!p.valid || !parent.valid) return@forEachIndexed
                    val d = dirs[i]
                    p.components[TransformComponent::class.java]?.apply {
                        setPosition(
                            Vector3(
                                at.x + d.x * t * 0.9f,
                                at.y + d.y * t - 0.6f * t * t,
                                at.z + d.z * t * 0.9f,
                            )
                        )
                        val k = (1f - t).coerceAtLeast(0.05f)
                        setScaleVector(Vector3(k, k, k))
                    }
                }
                delay(45)
            }
            particles.forEach { if (it.valid) it.destroy(true) }
        }
    }

    /** 生长弹跳：模型从小弹到大（0.7→1.05→1.0 回弹） */
    fun popScale(entity: Entity, target: Float) {
        scope.launch {
            val t0 = 0.65f
            val overshoot = target * 1.12f
            for (s in 1..8) {
                val k = t0 + (overshoot - t0) * (s / 8f)
                if (!entity.valid) return@launch
                entity.components[TransformComponent::class.java]
                    ?.setScaleVector(Vector3(k, k, k))
                delay(30)
            }
            for (s in 1..4) {
                val k = overshoot + (target - overshoot) * (s / 4f)
                if (!entity.valid) return@launch
                entity.components[TransformComponent::class.java]
                    ?.setScaleVector(Vector3(k, k, k))
                delay(28)
            }
        }
    }
}
