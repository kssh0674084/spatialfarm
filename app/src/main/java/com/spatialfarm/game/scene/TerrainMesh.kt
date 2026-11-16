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

    // 颜色（吉卜力暖阳调色板：降低荧光感，草色分层更柔）
    private val GRASS = Color4(0.50f, 0.74f, 0.35f, 1f)
    private val GRASS_LIGHT = Color4(0.70f, 0.86f, 0.42f, 1f)
    private val GRASS_DARK = Color4(0.31f, 0.52f, 0.30f, 1f)
    private val MOUND_TOP = Color4(0.82f, 0.88f, 0.47f, 1f)
    private val SAND = Color4(0.93f, 0.87f, 0.68f, 1f)
    private val SAND_WET = Color4(0.74f, 0.68f, 0.52f, 1f)
    private val ROCK = Color4(0.62f, 0.60f, 0.55f, 1f)
    private val SEABED = Color4(0.40f, 0.60f, 0.62f, 1f)
    private val SUN_DIR = Vector3(-0.38f, 0.80f, 0.46f) // 指向太阳（与场景主光一致）

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

        // 磨坊水塘（水车小屋西北侧，给水轮一个能浸入的水面）
        h -= gaussian(x, z, -9.40f, 8.60f, 1.15f) * 2.6f

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

        // 预计算高度（顶点色烘焙光影需要邻域高度，避免重复采样）
        val heights = FloatArray(vertexCount)
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                val pz = (iz.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                heights[iz * GRID + ix] = height(px, pz)
            }
        }

        return descriptor.use {
            RawMesh.create(it).use { rawMesh ->
                check(rawMesh.populateVertexBuffer(0) { buffer -> writePositions(buffer, heights) })
                check(rawMesh.populateVertexBuffer(1) { buffer -> writeColors(buffer, heights) })
                check(rawMesh.populateIndicesBuffer(::writeIndices))
                MeshResource.createWithRawMesh(rawMesh, "island_terrain")
            }
        }
    }

    // ---------------------------------------------------------------- 颜色

    /**
     * 顶点色烘焙：坡面明暗（伪光照）+ 凹地 AO + 陡坡岩石 + 湿沙岸 + 细微色噪。
     * 用 Unlit 材质渲染，但视觉上有体积感。
     */
    private fun colorAt(x: Float, z: Float, h: Float, nx: Float, ny: Float, nz: Float, noise: Float): Color4 {
        if (h < SEA_LEVEL + 0.25f) return SEABED
        // 沙滩只出现在岛边缘环带（内陆低地仍是草地）
        if (h < SEA_LEVEL + 0.9f && kotlin.math.hypot(x, z) > 34f) {
            val wet = ((SEA_LEVEL + 0.9f - h) / 0.9f).coerceIn(0f, 1f)
            return lerpColor(SAND, SAND_WET, wet * 0.6f)
        }

        val big = 0.5f + 0.5f * sin(x * 0.11f + 2.0f) * sin(z * 0.10f + 1.2f)
        val small = 0.5f + 0.5f * sin(x * 0.35f + 0.7f) * sin(z * 0.30f + 2.9f)
        val t = 0.65f * big + 0.35f * small
        var c = lerpColor(GRASS, GRASS_LIGHT, t)

        // 大尺度草甸阴影斑块（避免大片平涂）
        val patch = 0.5f + 0.5f * sin(x * 0.052f + 1.7f) * sin(z * 0.043f + 0.6f)
        if (patch < 0.38f) {
            c = lerpColor(c, GRASS_DARK, ((0.38f - patch) / 0.38f) * 0.55f)
        }

        if (h > 2.6f) {
            val top = ((h - 2.6f) / 1.6f).coerceAtMost(0.8f)
            c = lerpColor(c, MOUND_TOP, top)
        }
        if (h < 0.4f) {
            val low = ((0.4f - h) / 1.1f).coerceAtMost(0.85f)
            c = lerpColor(c, GRASS_DARK, low)
        }

        // 陡坡 → 岩石色
        val slope = 1f - ny
        if (slope > 0.16f) {
            val rockMix = ((slope - 0.16f) / 0.38f).coerceAtMost(0.8f)
            c = lerpColor(c, ROCK, rockMix)
        }

        // 坡面明暗：主光方向 N·L
        val ndotl = (nx * SUN_DIR.x + ny * SUN_DIR.y + nz * SUN_DIR.z).coerceIn(0f, 1f)
        val lit = 0.55f + 0.45f * ndotl
        // 凹地轻微 AO：邻域越低越暗
        var r = c.red * lit
        var g = c.green * lit
        var b = c.blue * lit
        // 受光面偏暖、背光面偏冷（吉卜力式色彩）
        r *= 1f + 0.04f * ndotl
        b *= 1f - 0.05f * ndotl + 0.03f * (1f - ndotl)
        // 细微色噪，打散平涂感
        r *= noise
        g *= noise
        b *= noise
        return Color4(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
    }

    // ---------------------------------------------------------------- 缓冲

    private fun writePositions(buffer: ByteBuffer, heights: FloatArray): Boolean {
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                val pz = (iz.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                val offset = index * POS_STRIDE
                buffer.putFloat(offset, px)
                buffer.putFloat(offset + 4, heights[index])
                buffer.putFloat(offset + 8, pz)
            }
        }
        return true
    }

    private fun writeColors(buffer: ByteBuffer, heights: FloatArray): Boolean {
        val step = HALF * 2f / (GRID - 1)
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                val pz = (iz.toFloat() / (GRID - 1) - 0.5f) * HALF * 2f
                val h = heights[index]

                val hl = heights[iz * GRID + (ix - 1).coerceAtLeast(0)]
                val hr = heights[iz * GRID + (ix + 1).coerceAtMost(GRID - 1)]
                val ht = heights[(iz - 1).coerceAtLeast(0) * GRID + ix]
                val hb = heights[(iz + 1).coerceAtMost(GRID - 1) * GRID + ix]
                var nx = -(hr - hl) / (2f * step)
                var ny = 1f
                var nz = -(hb - ht) / (2f * step)
                val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
                nx /= len
                ny /= len
                nz /= len

                // 伪 AO：比四邻平均低 → 暗
                val avg4 = (hl + hr + ht + hb) * 0.25f
                val conc = (h - avg4) / step
                val ao = (1f + conc * 0.10f).coerceIn(0.82f, 1.06f)

                val n = 0.94f + 0.12f * fract(kotlin.math.sin(ix * 12.9898f + iz * 78.233f) * 43758.5453f)
                val c = colorAt(px, pz, h, nx, ny, nz, n)
                val offset = index * COLOR_STRIDE
                buffer.putFloat(offset, (c.red * ao).coerceIn(0f, 1f))
                buffer.putFloat(offset + 4, (c.green * ao).coerceIn(0f, 1f))
                buffer.putFloat(offset + 8, (c.blue * ao).coerceIn(0f, 1f))
                buffer.putFloat(offset + 12, c.alpha)
            }
        }
        return true
    }

    private fun fract(v: Float): Float = v - kotlin.math.floor(v)

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
