package com.spatialfarm.game.scene.fx

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshIndexSizeType
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshVertexAttribute
import com.pico.spatial.core.ecs.resource.MeshVertexAttributeType
import com.pico.spatial.core.ecs.resource.PrimitiveType
import com.pico.spatial.core.ecs.resource.RawMesh
import com.pico.spatial.core.ecs.resource.RawMeshDescriptorBuilder
import com.pico.spatial.core.ecs.resource.RawMeshStreamAttribute
import com.pico.spatial.core.ecs.resource.RawMeshStreamLayout
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 雨幕：一条网格包含许多细长雨丝（交叉双面片），
 * 由若干层实体上下循环滚动形成降雨。
 */
object RainMesh {
    private const val POS_STRIDE = 12
    private const val COLOR_STRIDE = 16
    private const val STREAKS = 620
    private const val AREA = 11f      // 雨区边长（米）
    private const val HEIGHT = 10f    // 雨区高度

    fun createMaterial(): UnlitMaterial =
        UnlitMaterial.create().apply {
            setBaseColor(Color4(0.72f, 0.82f, 0.95f, 1f))
            setCullingMode(MaterialCullingMode.NONE)
            toGlobal()
        }

    fun create(): MeshResource {
        val rnd = Random(77)
        // 每条雨丝 = 2 个交叉四边形 = 4 三角形 = 12 顶点
        val vertexCount = STREAKS * 12
        val tris = STREAKS * 4
        val positions = FloatArray(vertexCount * 3)
        val colors = FloatArray(vertexCount * 4)

        var v = 0
        fun put(x: Float, y: Float, z: Float, shade: Float) {
            positions[v * 3] = x
            positions[v * 3 + 1] = y
            positions[v * 3 + 2] = z
            colors[v * 4] = 0.72f * shade
            colors[v * 4 + 1] = 0.82f * shade
            colors[v * 4 + 2] = 0.95f * shade
            colors[v * 4 + 3] = 1f
            v++
        }

        fun quad(cx: Float, cy: Float, cz: Float, dx: Float, dz: Float, w: Float, h: Float, shade: Float) {
            val x1 = cx - dx * w
            val z1 = cz - dz * w
            val x2 = cx + dx * w
            val z2 = cz + dz * w
            put(x1, cy - h, z1, shade); put(x2, cy - h, z2, shade); put(x2, cy + h, z2, shade)
            put(x1, cy - h, z1, shade); put(x2, cy + h, z2, shade); put(x1, cy + h, z1, shade)
        }

        var made = 0
        while (made < STREAKS) {
            val x = (rnd.nextFloat() - 0.5f) * AREA
            val z = (rnd.nextFloat() - 0.5f) * AREA
            if (kotlin.math.hypot(x, z) < 1.6f) continue // 相机附近留空，避免巨大雨条
            val y = rnd.nextFloat() * HEIGHT
            val h = 0.28f + rnd.nextFloat() * 0.28f
            val w = 0.004f + rnd.nextFloat() * 0.004f
            val shade = 0.85f + rnd.nextFloat() * 0.25f
            val a = rnd.nextFloat() * 3.1416f
            // 两个垂直交叉的面片
            quad(x, y, z, cos(a), sin(a), w, h, shade)
            quad(x, y, z, -sin(a), cos(a), w, h, shade)
            made++
        }

        val descriptor = RawMeshDescriptorBuilder().use { builder ->
            builder
                .streamLayout(RawMeshStreamLayout(bufferSlot = 0, stride = POS_STRIDE))
                .streamLayout(RawMeshStreamLayout(bufferSlot = 1, stride = COLOR_STRIDE))
                .streamAttribute(
                    RawMeshStreamAttribute(
                        MeshVertexAttribute.POSITION,
                        MeshVertexAttributeType.FLOAT3,
                        recordByteOffset = 0,
                        streamLayoutIndex = 0,
                    )
                )
                .streamAttribute(
                    RawMeshStreamAttribute(
                        MeshVertexAttribute.COLOR,
                        MeshVertexAttributeType.FLOAT4,
                        recordByteOffset = 0,
                        streamLayoutIndex = 1,
                    )
                )
                .vertexCount(vertexCount)
                .indexCount(tris * 3)
                .indexType(MeshIndexSizeType.UNSIGNED_SHORT)
                .isDynamic(false)
                .boundingBox(
                    BoundingBox(
                        center = Vector3(0f, HEIGHT * 0.5f, 0f),
                        halfExtent = Vector3(AREA, HEIGHT, AREA),
                    )
                )
                .addSubMesh(0, tris * 3, 0, "rain", PrimitiveType.TRIANGLES)
                .build()
        }

        return descriptor.use {
            RawMesh.create(it).use { rawMesh ->
                check(rawMesh.populateVertexBuffer(0) { buffer: ByteBuffer ->
                    for (i in 0 until vertexCount) {
                        val o = i * POS_STRIDE
                        buffer.putFloat(o, positions[i * 3])
                        buffer.putFloat(o + 4, positions[i * 3 + 1])
                        buffer.putFloat(o + 8, positions[i * 3 + 2])
                    }
                    true
                })
                check(rawMesh.populateVertexBuffer(1) { buffer: ByteBuffer ->
                    for (i in 0 until vertexCount) {
                        val o = i * COLOR_STRIDE
                        buffer.putFloat(o, colors[i * 4])
                        buffer.putFloat(o + 4, colors[i * 4 + 1])
                        buffer.putFloat(o + 8, colors[i * 4 + 2])
                        buffer.putFloat(o + 12, colors[i * 4 + 3])
                    }
                    true
                })
                check(rawMesh.populateIndicesBuffer { buffer: ByteBuffer ->
                    var o = 0
                    for (i in 0 until vertexCount) {
                        buffer.putShort(o, i.toShort())
                        o += 2
                    }
                    true
                })
                MeshResource.createWithRawMesh(rawMesh, "rain_mesh")
            }
        }
    }

    /** 创建多层雨幕实体（跟随世界根节点）。 */
    fun createRain(seed: Int = 3): Entity {
        val root = Entity()
        root.setName("rain_root")
        val mesh = create().also { it.toGlobal() }
        val mat = createMaterial()
        val rnd = Random(seed)
        repeat(4) { layer ->
            val e = ModelEntity(mesh, mat)
            e.setName("rain_layer_$layer")
            e.components[TransformComponent::class.java]!!.apply {
                setPosition(
                    Vector3(
                        (rnd.nextFloat() - 0.5f) * 2f,
                        layer * (HEIGHT / 4f),
                        (rnd.nextFloat() - 0.5f) * 2f,
                    )
                )
            }
            root.addChild(e)
        }
        return root
    }
}
