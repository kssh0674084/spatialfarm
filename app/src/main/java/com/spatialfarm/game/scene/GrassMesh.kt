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
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 程序化草地：把上千丛草叶 + 小野花烘成**一个** RawMesh（单 draw call），
 * 顶点色渐变（根部深、叶尖亮），双面渲染，不依赖贴图。
 *
 * 排除区域：农田、村舍、小路、湖/水塘/溪流、水面以下。
 */
object GrassMesh {
    private const val POS_STRIDE = 12
    private const val COLOR_STRIDE = 16

    // 小路（与 VillageScene.buildTrail 保持一致）
    private val PATHS = listOf(
        listOf(
            floatArrayOf(-2f, -3f), floatArrayOf(-7f, 2f), floatArrayOf(-12f, 7f),
            floatArrayOf(-16f, 11f), floatArrayOf(-18f, 13.5f),
        ),
        listOf(
            floatArrayOf(3f, -4f), floatArrayOf(9f, -7f), floatArrayOf(15f, -10f),
            floatArrayOf(20f, -13f), floatArrayOf(23f, -15f),
        ),
        listOf(
            floatArrayOf(2f, 1f), floatArrayOf(4f, 8f), floatArrayOf(6f, 15f),
            floatArrayOf(7f, 22f),
        ),
    )

    private fun segDist(px: Float, pz: Float, x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x2 - x1
        val dz = z2 - z1
        val len2 = dx * dx + dz * dz
        if (len2 <= 0.0001f) return hypot(px - x1, pz - z1)
        var t = ((px - x1) * dx + (pz - z1) * dz) / len2
        t = t.coerceIn(0f, 1f)
        return hypot(px - (x1 + t * dx), pz - (z1 + t * dz))
    }

    private fun nearPath(x: Float, z: Float, limit: Float): Boolean {
        for (path in PATHS) {
            for (i in 0 until path.size - 1) {
                if (segDist(x, z, path[i][0], path[i][1], path[i + 1][0], path[i + 1][1]) < limit) {
                    return true
                }
            }
        }
        return false
    }

    private fun blocked(x: Float, z: Float, h: Float): Boolean {
        if (h < -0.05f) return true
        if (hypot(x + 20f, z - 16f) < 12.8f) return true      // 湖
        if (hypot(x + 9.4f, z - 8.6f) < 2.7f) return true     // 磨坊水塘
        if (nearPath(x, z, 1.5f)) return true
        // 农田矩形
        if (x > -4.9f && x < 4.9f && z > -4.6f && z < 0.7f) return true
        // 村庄建筑区
        if (hypot(x - 0f, z + 5.5f) < 5.2f) return true
        // 溪流
        for (i in 0 until TerrainMesh.streamSegments().size - 1) {
            val s = TerrainMesh.streamSegments()
            if (segDist(x, z, s[i][0], s[i][1], s[i + 1][0], s[i + 1][1]) < 3.2f) return true
        }
        return false
    }

    /** 生成整片草地（一个网格）。 */
    fun create(): MeshResource {
        val rnd = Random(20261116)

        data class Tuft(val x: Float, val z: Float, val h: Float, val tint: Float)

        val tufts = ArrayList<Tuft>(3200)
        var guard = 0
        while (tufts.size < 3200 && guard < 40000) {
            guard++
            val near = tufts.size < 2100
            val radius = if (near) 3f + rnd.nextFloat() * 19f else 20f + rnd.nextFloat() * 24f
            val a = rnd.nextFloat() * 6.2831855f
            val x = cos(a) * radius
            val z = sin(a) * radius
            val h = TerrainMesh.height(x, z)
            if (blocked(x, z, h)) continue
            tufts.add(Tuft(x, z, h, rnd.nextFloat()))
        }

        val flowerPalette = listOf(
            Color4(0.97f, 0.96f, 0.90f, 1f), // 白
            Color4(1.00f, 0.86f, 0.38f, 1f), // 黄
            Color4(0.96f, 0.62f, 0.72f, 1f), // 粉
            Color4(0.72f, 0.64f, 0.95f, 1f), // 淡紫
        )

        val bladesPerTuft = 4
        val triCount = tufts.size * bladesPerTuft + 320
        val vertexCount = triCount * 3

        val positions = FloatArray(vertexCount * 3)
        val colors = FloatArray(vertexCount * 4)

        var v = 0
        fun put(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
            positions[v * 3] = x
            positions[v * 3 + 1] = y
            positions[v * 3 + 2] = z
            colors[v * 4] = r
            colors[v * 4 + 1] = g
            colors[v * 4 + 2] = b
            colors[v * 4 + 3] = 1f
            v++
        }

        for (t in tufts) {
            val dry = t.tint > 0.86f
            val baseR = if (dry) 0.38f else 0.24f
            val baseG = if (dry) 0.41f else 0.40f
            val baseB = if (dry) 0.16f else 0.17f
            val tipR = if (dry) 0.76f else 0.56f
            val tipG = if (dry) 0.73f else 0.76f
            val tipB = if (dry) 0.31f else 0.32f
            val tintVar = 0.94f + 0.12f * t.tint

            for (blade in 0 until bladesPerTuft) {
                val yaw = rnd.nextFloat() * 6.2831855f
                val halfW = 0.022f + rnd.nextFloat() * 0.020f
                val height = 0.16f + rnd.nextFloat() * 0.24f
                val lean = 0.03f + rnd.nextFloat() * 0.09f
                val bx = t.x + cos(yaw + 1.57f) * (rnd.nextFloat() - 0.5f) * 0.16f
                val bz = t.z + sin(yaw + 1.57f) * (rnd.nextFloat() - 0.5f) * 0.16f
                val px = cos(yaw) * halfW
                val pz = sin(yaw) * halfW
                val tipX = bx + cos(yaw) * lean
                val tipZ = bz + sin(yaw) * lean

                put(bx - px, t.h + 0.005f, bz - pz, baseR * tintVar, baseG * tintVar, baseB * tintVar)
                put(bx + px, t.h + 0.005f, bz + pz, baseR * tintVar, baseG * tintVar, baseB * tintVar)
                put(tipX, t.h + height, tipZ, tipR * tintVar, tipG * tintVar, tipB * tintVar)
            }
        }

        // 小野花（彩色小三角）撒在草地上
        var flowers = 0
        var fguard = 0
        while (flowers < 320 && fguard < 20000) {
            fguard++
            val radius = 4f + rnd.nextFloat() * 30f
            val a = rnd.nextFloat() * 6.2831855f
            val x = cos(a) * radius
            val z = sin(a) * radius
            val h = TerrainMesh.height(x, z)
            if (blocked(x, z, h)) continue
            val c = flowerPalette[rnd.nextInt(flowerPalette.size)]
            val yaw = rnd.nextFloat() * 6.2831855f
            val size = 0.035f + rnd.nextFloat() * 0.03f
            val stemH = 0.10f + rnd.nextFloat() * 0.14f
            val px = cos(yaw) * size
            val pz = sin(yaw) * size
            // 细茎
            put(x, h + 0.005f, z, 0.35f, 0.50f, 0.28f)
            // 花冠两片（薄十字，双面渲染时可见）
            put(x - px, h + stemH, z - pz, c.red, c.green, c.blue)
            put(x + px, h + stemH, z + pz, c.red, c.green, c.blue)
            flowers++
        }

        val usedVerts = v
        val usedTris = usedVerts / 3

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
                .vertexCount(usedVerts)
                .indexCount(usedTris * 3)
                .indexType(MeshIndexSizeType.UNSIGNED_SHORT)
                .isDynamic(false)
                .boundingBox(
                    BoundingBox(
                        center = Vector3(0f, 0f, 0f),
                        halfExtent = Vector3(TerrainMesh.HALF, 4f, TerrainMesh.HALF),
                    )
                )
                .addSubMesh(0, usedTris * 3, 0, "grass", PrimitiveType.TRIANGLES)
                .build()
        }

        return descriptor.use {
            RawMesh.create(it).use { rawMesh ->
                check(rawMesh.populateVertexBuffer(0) { buffer: ByteBuffer ->
                    for (i in 0 until usedVerts) {
                        val o = i * POS_STRIDE
                        buffer.putFloat(o, positions[i * 3])
                        buffer.putFloat(o + 4, positions[i * 3 + 1])
                        buffer.putFloat(o + 8, positions[i * 3 + 2])
                    }
                    true
                })
                check(rawMesh.populateVertexBuffer(1) { buffer: ByteBuffer ->
                    for (i in 0 until usedVerts) {
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
                    for (idx in 0 until usedVerts) {
                        buffer.putShort(o, idx.toShort())
                        o += 2
                    }
                    true
                })
                MeshResource.createWithRawMesh(rawMesh, "meadow_grass")
            }
        }
    }
}
