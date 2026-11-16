package com.spatialfarm.game.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshIndexSizeType
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshVertexAttribute
import com.pico.spatial.core.ecs.resource.MeshVertexAttributeType
import com.pico.spatial.core.ecs.resource.PrimitiveType
import com.pico.spatial.core.ecs.resource.RawMesh
import com.pico.spatial.core.ecs.resource.RawMeshDescriptorBuilder
import com.pico.spatial.core.ecs.resource.RawMeshStreamAttribute
import com.pico.spatial.core.ecs.resource.RawMeshStreamLayout
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 程序化远山：带径向噪声的山脊 + 顶点色（岩层渐变 / 不规则雪线 / 底部大气雾）。
 * 仍是 Unlit 顶点色渲染，但比纯色圆锥有层次得多。
 * 单位尺寸：底面半径 1，高度 1，摆放时用 scale 控制。
 */
object MountainMesh {
    private const val SEG = 20
    private const val RINGS = 7
    private const val POS_STRIDE = 12
    private const val COLOR_STRIDE = 16

    private val ROCK_LOW = Color4(0.32f, 0.38f, 0.52f, 1f)
    private val ROCK_HIGH = Color4(0.47f, 0.53f, 0.69f, 1f)
    private val SNOW = Color4(0.92f, 0.94f, 1f, 1f)
    private val HAZE = Color4(0.71f, 0.81f, 0.93f, 1f)

    private fun ridge(angle: Float, seed: Int): Float =
        1f + 0.22f * sin(3f * angle + seed) +
            0.12f * sin(7f * angle + seed * 2.3f) +
            0.07f * sin(13f * angle + seed * 0.7f)

    fun create(seed: Int): MeshResource {
        val ringVerts = RINGS * SEG
        val vertexCount = ringVerts + 1 // 顶点
        val indexCount = (RINGS - 1) * SEG * 6 + SEG * 3

        val positions = FloatArray(vertexCount * 3)
        val colors = FloatArray(vertexCount * 4)

        fun putVertex(i: Int, x: Float, y: Float, z: Float, t: Float, angle: Float) {
            positions[i * 3] = x
            positions[i * 3 + 1] = y
            positions[i * 3 + 2] = z

            // 岩石渐变
            val rockT = 0.5f + 0.5f * sin(angle * 5f + seed + t * 3f)
            var r = ROCK_LOW.red + (ROCK_HIGH.red - ROCK_LOW.red) * rockT
            var g = ROCK_LOW.green + (ROCK_HIGH.green - ROCK_LOW.green) * rockT
            var b = ROCK_LOW.blue + (ROCK_HIGH.blue - ROCK_LOW.blue) * rockT

            // 不规则雪线
            val snowLine = 0.70f + 0.08f * sin(4f * angle + seed) + 0.04f * sin(9f * angle + seed * 1.7f)
            val snowT = ((t - snowLine) / 0.05f).coerceIn(0f, 1f)
            r += (SNOW.red - r) * snowT
            g += (SNOW.green - g) * snowT
            b += (SNOW.blue - b) * snowT

            // 底部大气雾（越靠下越融入天空）
            val hazeT = (1f - t) * (1f - t) * 0.16f
            r += (HAZE.red - r) * hazeT
            g += (HAZE.green - g) * hazeT
            b += (HAZE.blue - b) * hazeT

            // 竖向明暗（岩壁层次）
            val shade = 0.90f + 0.10f * sin(angle * 2f + 1.3f)
            colors[i * 4] = (r * shade).coerceIn(0f, 1f)
            colors[i * 4 + 1] = (g * shade).coerceIn(0f, 1f)
            colors[i * 4 + 2] = (b * shade).coerceIn(0f, 1f)
            colors[i * 4 + 3] = 1f
        }

        for (ring in 0 until RINGS) {
            val t = ring / RINGS.toFloat()
            val y = t
            for (seg in 0 until SEG) {
                val angle = seg / SEG.toFloat() * 6.2831855f
                val radius = (1f - t) * (1f - t * 0.25f) * ridge(angle, seed)
                val jitterY = 0.05f * sin(9f * angle + seed * 0.9f) * t * (1f - t) * 4f
                putVertex(
                    ring * SEG + seg,
                    cos(angle) * radius,
                    y + jitterY,
                    sin(angle) * radius,
                    t,
                    angle,
                )
            }
        }
        // 顶点
        val apex = ringVerts
        putVertex(apex, 0f, 1f, 0f, 1f, 0.3f)

        val indices = IntArray(indexCount)
        var k = 0
        for (ring in 0 until RINGS - 1) {
            for (seg in 0 until SEG) {
                val a = ring * SEG + seg
                val b = ring * SEG + (seg + 1) % SEG
                val c = (ring + 1) * SEG + seg
                val d = (ring + 1) * SEG + (seg + 1) % SEG
                indices[k++] = a; indices[k++] = c; indices[k++] = b
                indices[k++] = b; indices[k++] = c; indices[k++] = d
            }
        }
        val lastRing = (RINGS - 1) * SEG
        for (seg in 0 until SEG) {
            val a = lastRing + seg
            val b = lastRing + (seg + 1) % SEG
            indices[k++] = a; indices[k++] = apex; indices[k++] = b
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
                .indexCount(indexCount)
                .indexType(MeshIndexSizeType.UNSIGNED_SHORT)
                .isDynamic(false)
                .boundingBox(
                    BoundingBox(
                        center = Vector3(0f, 0.5f, 0f),
                        halfExtent = Vector3(1.2f, 0.55f, 1.2f),
                    )
                )
                .addSubMesh(0, indexCount, 0, "mountain", PrimitiveType.TRIANGLES)
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
                    for (v in indices) {
                        buffer.putShort(o, v.toShort())
                        o += 2
                    }
                    true
                })
                MeshResource.createWithRawMesh(rawMesh, "mountain_$seed")
            }
        }
    }
}
