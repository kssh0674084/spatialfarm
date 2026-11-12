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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 旷野之息风格的程序化草原地形（RawMesh）。
 *
 * - 多层正弦叠加生成柔和起伏的丘陵（非噪声，平滑吉卜力感）
 * - 岛屿边缘自然下沉，形成草丘小岛的轮廓
 * - 村庄功能区（田垄/主街/小屋/码头/用户站立区）自动压平，物体稳定落地
 * - 每顶点颜色：三种粉彩绿按斑块混合，丘顶泛奶油黄、边缘转深绿
 *
 * 坐标约定：地形覆盖 X ∈ [-8, 8]，Z ∈ [-9.5, 4.5]，中心 (0, -2.5)。
 */
object TerrainMesh {

    /** 压平区（世界坐标圆盘）：中心 + 半径。区内高度平滑过渡到 0。 */
    private val FLAT_ZONES = listOf(
        FlatZone(0.0f, 0.6f, 2.2f),      // 用户站立区
        FlatZone(0.0f, -2.7f, 5.0f),     // 田垄区（8 块田 2×4）
        FlatZone(0.0f, -6.2f, 4.4f),     // 小镇主街
        FlatZone(-4.2f, -1.2f, 2.6f),    // 玩家小屋
        FlatZone(5.6f, -2.2f, 2.4f),     // 码头
    )

    private data class FlatZone(val cx: Float, val cz: Float, val radius: Float)

    // 网格参数
    private const val GRID = 72                 // 72×72 顶点，间距约 0.2m
    private const val HALF_W = 8f               // X 半宽
    private const val CENTER_Z = -2.5f
    private const val HALF_D = 7f               // Z 半深

    // 双流布局：槽 0 = POSITION(FLOAT3, 12B)，槽 1 = COLOR(FLOAT4, 16B)
    private const val POS_STRIDE = 3 * 4
    private const val COLOR_STRIDE = 4 * 4

    // 粉彩草色（加大对比，旷野之息式斑块）
    private val GRASS = Color4(0.64f, 0.82f, 0.52f, 1f)
    private val GRASS_LIGHT = Color4(0.84f, 0.94f, 0.58f, 1f)
    private val GRASS_DARK = Color4(0.40f, 0.62f, 0.40f, 1f)
    private val MOUND_TOP = Color4(0.93f, 0.93f, 0.62f, 1f)   // 丘顶阳光黄

    /** 生成并返回草原地形网格。 */
    fun create(): MeshResource {
        val vertexCount = GRID * GRID
        val indexCount = (GRID - 1) * (GRID - 1) * 6

        val descriptor = RawMeshDescriptorBuilder().use { builder ->
            builder
                // 流布局 0：位置流（bufferSlot 0）
                .streamLayout(RawMeshStreamLayout(bufferSlot = 0, stride = POS_STRIDE))
                // 流布局 1：颜色流（bufferSlot 1）
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
                        center = Vector3(0f, 0f, CENTER_Z),
                        halfExtent = Vector3(HALF_W, 1.2f, HALF_D),
                    )
                )
                .addSubMesh(0, indexCount, 0, "meadow", PrimitiveType.TRIANGLES)
                .build()
        }

        return descriptor.use {
            RawMesh.create(it).use { rawMesh ->
                check(rawMesh.populateVertexBuffer(0) { buffer -> writePositions(buffer) })
                check(rawMesh.populateVertexBuffer(1) { buffer -> writeColors(buffer) })
                check(rawMesh.populateIndicesBuffer(::writeIndices))
                MeshResource.createWithRawMesh(rawMesh, "village_meadow")
            }
        }
    }

    // ---------------------------------------------------------------- 高度与颜色

    /** 起伏高度场：多层柔和正弦 + 边缘下沉 + 功能区压平。 */
    fun height(x: Float, z: Float): Float {
        var h = 0.32f * sin(x * 0.32f + 1.7f) * cos(z * 0.27f + 0.6f) +
            0.15f * sin(x * 0.85f + 4.2f) * sin(z * 0.78f + 2.1f) +
            0.07f * cos(x * 1.7f + 0.8f) * cos(z * 1.6f + 3.3f) +
            0.28f

        // 岛屿边缘：径向 falloff，外围下沉
        val nx = x / HALF_W
        val nz = (z - CENTER_Z) / HALF_D
        val r = sqrt(nx * nx + nz * nz)
        h -= smoothStep(0.70f, 1.05f, r) * 0.85f

        // 功能区压平到 0
        for (zone in FLAT_ZONES) {
            val d = hypot(x - zone.cx, z - zone.cz)
            val inner = zone.radius * 0.55f
            if (d < inner) {
                h = 0f
            } else if (d < zone.radius) {
                val k = smoothStep(inner, zone.radius, d)
                h *= k
            }
        }
        return h
    }

    /** 顶点草色：双频斑块混合 + 丘顶阳光 + 低洼深绿。 */
    private fun colorAt(x: Float, z: Float, h: Float): Color4 {
        val big = 0.5f + 0.5f * sin(x * 0.45f + 2.0f) * sin(z * 0.40f + 1.2f)
        val small = 0.5f + 0.5f * sin(x * 1.3f + 0.7f) * sin(z * 1.1f + 2.9f)
        val t = 0.65f * big + 0.35f * small
        var c = lerpColor(GRASS, GRASS_LIGHT, t)

        val top = if (h > 0.30f) (h - 0.30f) / 0.35f else 0f
        if (top > 0f) c = lerpColor(c, MOUND_TOP, top.coerceAtMost(0.8f))

        val low = if (h < -0.10f) (-0.10f - h) / 0.6f else 0f
        if (low > 0f) c = lerpColor(c, GRASS_DARK, low.coerceAtMost(0.9f))

        return c
    }

    // ---------------------------------------------------------------- 缓冲写入

    private fun writePositions(buffer: ByteBuffer): Boolean {
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF_W * 2f
                val pz = CENTER_Z + (iz.toFloat() / (GRID - 1) - 0.5f) * HALF_D * 2f
                val py = height(px, pz)

                val offset = index * POS_STRIDE
                buffer.putFloat(offset, px)
                buffer.putFloat(offset + 4, py)
                buffer.putFloat(offset + 8, pz)
            }
        }
        return true
    }

    private fun writeColors(buffer: ByteBuffer): Boolean {
        for (iz in 0 until GRID) {
            for (ix in 0 until GRID) {
                val index = iz * GRID + ix
                val px = (ix.toFloat() / (GRID - 1) - 0.5f) * HALF_W * 2f
                val pz = CENTER_Z + (iz.toFloat() / (GRID - 1) - 0.5f) * HALF_D * 2f
                val py = height(px, pz)
                val c = colorAt(px, pz, py)

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

    /** 地形是否在某压平区内（供放置物体时参考）。 */
    fun isFlatZone(x: Float, z: Float): Boolean =
        FLAT_ZONES.any { hypot(x - it.cx, z - it.cz) < it.radius * 0.55f && abs(it.cx) >= 0f }
}
