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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 开放世界岛屿地形 v2（100×100 米，吉卜力暖阳风）。
 *
 * 结构：
 * - 中央：村庄高原（压平）
 * - 风车丘陵（东侧隆起）
 * - 湖泊盆地（西北）+ 小溪（湖向东南入海）
 * - 边缘沙滩 → 环海
 */
object TerrainMesh {

    const val HALF = 50f                 // 岛屿半宽（X/Z 各 -50..50）
    const val SEA_LEVEL = -0.8f          // 海面高度
    const val LAKE_LEVEL = -0.30f        // 湖面高度
    private const val GRID = 240
    private const val POS_STRIDE = 12
    private const val COLOR_STRIDE = 16

    // 溪流路径（湖 → 海）
    private val STREAM_PATH = listOf(
        floatArrayOf(-20f, 16f),
        floatArrayOf(-14f, 20f),
        floatArrayOf(-8f, 25f),
        floatArrayOf(-2f, 30f),
        floatArrayOf(3f, 35f),
        floatArrayOf(7f, 40f),
        floatArrayOf(9f, 45f),
    )

    // 颜色
    private val GRASS = Color4(0.55f, 0.80f, 0.38f, 1f)
    private val GRASS_LIGHT = Color4(0.80f, 0.93f, 0.46f, 1f)
    private val GRASS_DARK = Color4(0.32f, 0.58f, 0.33f, 1f)
    private val MOUND_TOP = Color4(0.94f, 0.92f, 0.52f, 1f)
    private val SAND = Color4(0.93f, 0.87f, 0.66f, 1f)
    private val SEABED = Color4(0.42f, 0.62f, 0.62f, 1f)

    /** 主高度场 */
    fun height(x: Float, z: Float): Float {
        var h = 1.6f * sin(x * 0.075f + 1.3f) * cos(z * 0.06f + 0.4f) +
            0.9f * sin(x * 0.16f + 4.1f) * sin(z * 0.14f + 2.2f) +
            0.35f * cos(x * 0.42f + 0.9f) * cos(z * 0.38f + 3.1f) +
            0.15f * sin(x * 1.1f + 2.0f) * sin(z * 1.0f + 0.7f) +
            0.8f

        // 风车丘陵（东侧隆起）
        h += gaussian(x, z, 24f, -16f, 14f) * 4.2f

        // 湖泊盆地
        h -= gaussian(x, z, -20f, 16f, 11f) * 2.4f

        // 溪流河道下切
        var streamInfluence = 0f
        for (i in 0 until STREAM_PATH.size - 1) {
            val d = distToSegment(
                x, z,
                STREAM_PATH[i][0], STREAM_PATH[i][1],
                STREAM_PATH[i + 1][0], STREAM_PATH[i + 1][1],
            )
            streamInfluence = max(streamInfluence, 1f - min(1f, d / 2.2f))
        }
        h -= streamInfluence * 2.0f

        // 岛屿边缘下沉到海底
        val r = hypot(x, z)
        val edge = smoothStep(44f, 50f, r)
        h = h * (1f - edge) + (-2.6f) * edge

        // 村庄高原压平
        val plateau = 1f - smoothStep(12f, 17f, hypot(x, z - (-2.5f)))
        h *= (1f - plateau)

        return h
    }

    /** 溪流路径（水面条带用） */
    fun streamSegments(): List<FloatArray> = STREAM_PATH

    /** 是否可行走（海滩以内） */
    fun walkable(x: Float, z: Float): Boolean = hypot(x, z) < 46f

    /** 生成地形网格 */
    fun create(): MeshResource {
        val vertexCount = GRID * GRID
        val indexCount = (GRID - 1) * (GRID - 1) * 6

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
                        center = Vector3(0f, 0f, 0f),
                        halfExtent = Vector3(HALF, 6f, HALF),
                    )
                )
                .addSubMesh(0, indexCount, 0, "island", PrimitiveType.TRIANGLES)
                .build()
        }

        return descriptor.use {
            RawMesh.create(it).use { rawMesh ->
                check(rawMesh.populateVertexBuffer(0) { buffer -> writePositions(buffer) })
                check(rawMesh.populateVertexBuffer(1) { buffer -> writeColors(buffer) })
                check(rawMesh.populateIndicesBuffer(::writeIndices))
                MeshResource.createWithRawMesh(rawMesh, "island_terrain")
            }
        }
    }

    // ---------------------------------------------------------------- 颜色

    private fun colorAt(x: Float, z: Float, h: Float): Color4 {
        if (h < SEA_LEVEL + 0.25f) return SEABED
        // 沙滩只出现在岛边缘环带（内陆低地仍是草地）
        if (h < SEA_LEVEL + 0.9f && kotlin.math.hypot(x, z) > 34f) return SAND

        val big = 0.5f + 0.5f * sin(x * 0.11f + 2.0f) * sin(z * 0.10f + 1.2f)
        val small = 0.5f + 0.5f * sin(x * 0.35f + 0.7f) * sin(z * 0.30f + 2.9f)
        val t = 0.65f * big + 0.35f * small
        var c = lerpColor(GRASS, GRASS_LIGHT, t)

        if (h > 2.6f) {
            val top = ((h - 2.6f) / 1.6f).coerceAtMost(0.8f)
            c = lerpColor(c, MOUND_TOP, top)
        }
        if (h < 0.4f) {
            val low = ((0.4f - h) / 1.1f).coerceAtMost(0.85f)
            c = lerpColor(c, GRASS_DARK, low)
        }
        return c
    }

    // ---------------------------------------------------------------- 缓冲

    private fun gridPos(ix: Int, iz: Int): FloatArray {
        val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
        val pz = (iz.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
        return floatArrayOf(px, height(px, pz), pz)
    }

    private fun writePositions(buffer: ByteBuffer): Boolean {
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val p = gridPos(ix, iz)
                val offset = index * POS_STRIDE
                buffer.putFloat(offset, p[0])
                buffer.putFloat(offset + 4, p[1])
                buffer.putFloat(offset + 8, p[2])
            }
        }
        return true
    }

    private fun writeColors(buffer: ByteBuffer): Boolean {
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val p = gridPos(ix, iz)
                val c = colorAt(p[0], p[2], p[1])
                val offset = index * COLOR_STRIDE
                buffer.putFloat(offset, c.red)
                buffer.putFloat(offset + 4, c.green)
                buffer.putFloat(offset + 8, c.blue)
                buffer.putFloat(offset + 12, c.alpha)
            }
        }
        return true
    }

    private fun writeIndices(buffer: ByteBuffer): Boolean {
        var offset = 0
        for (iz in 0 until GRID - 1) {
            for (ix in 0 until GRID - 1) {
                val topLeft = iz * GRID + ix
                val bottomLeft = (iz + 1) * GRID + ix
                intArrayOf(
                    topLeft,
                    bottomLeft,
                    topLeft + 1,
                    topLeft + 1,
                    bottomLeft,
                    bottomLeft + 1,
                ).forEach { index ->
                    buffer.putShort(offset, index.toShort())
                    offset += 2
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------- 工具

    private fun gaussian(x: Float, z: Float, cx: Float, cz: Float, radius: Float): Float {
        val d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz)
        return kotlin.math.exp((-d2 / (radius * radius)).toDouble()).toFloat()
    }

    private fun distToSegment(
        px: Float, pz: Float,
        x1: Float, z1: Float,
        x2: Float, z2: Float,
    ): Float {
        val dx = x2 - x1
        val dz = z2 - z1
        val len2 = dx * dx + dz * dz
        if (len2 <= 0.0001f) return hypot(px - x1, pz - z1)
        var t = ((px - x1) * dx + (pz - z1) * dz) / len2
        t = t.coerceIn(0f, 1f)
        val cx = x1 + t * dx
        val cz = z1 + t * dz
        return hypot(px - cx, pz - cz)
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpColor(a: Color4, b: Color4, t: Float): Color4 {
        val k = t.coerceIn(0f, 1f)
        return Color4(
            a.red + (b.red - a.red) * k,
            a.green + (b.green - a.green) * k,
            a.blue + (b.blue - a.blue) * k,
            1f,
        )
    }
}
