package com.spatialfarm.game.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import kotlin.math.hypot

/**
 * 开放世界移动控制：
 * - 手柄左摇杆 → 平滑移动（按头部朝向换算前后左右）
 * - 脚下自动跟随地形高度（高度场解析计算）
 * - 玩家位置用 Compose 状态暴露（地图窗口实时标记）
 */
object Locomotion {
    private val controllerProvider = ControllerTrackingProvider()
    private val hmdProvider = HMDTrackingProvider()

    @Volatile private var stickX = 0f
    @Volatile private var stickY = 0f

    /** 头部前向（世界空间，已投影到 XZ 且归一化） */
    @Volatile private var fwdX = 0f
    @Volatile private var fwdZ = -1f

    /** 玩家世界坐标（Compose 可观察） */
    var playerX by mutableFloatStateOf(0f)
        private set
    var playerZ by mutableFloatStateOf(0f)
        private set

    /** 当前移动速度（m/s） */
    var speed = 3.0f

    /** 传送（地图快速旅行/调试用） */
    fun teleport(x: Float, z: Float) {
        if (!TerrainMesh.walkable(x, z)) return
        playerX = x
        playerZ = z
        groundHeight = TerrainMesh.height(x, z)
    }

    /** 当前脚下地面高度（世界坐标） */
    var groundHeight = 0f
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        runCatching {
            controllerProvider.addControllerActionListener { data ->
                val t = data.left.thumbstickValue
                stickX = t.x
                stickY = t.y
            }
            controllerProvider.start()
        }
        runCatching {
            hmdProvider.addListener { data ->
                val q = data.hmdPose.rotation
                // 四元数旋转 (0,0,-1)：f = ( -2(xz+wy), y分量, -(1-2(x²+y²)) )
                val fx = -2f * (q.x * q.z + q.w * q.y)
                val fz = -(1f - 2f * (q.x * q.x + q.y * q.y))
                val len = hypot(fx, fz)
                if (len > 0.001f) {
                    fwdX = fx / len
                    fwdZ = fz / len
                }
            }
            hmdProvider.start()
        }
    }

    /** 头部前向（世界 XZ，已归一化），给地图标记用 */
    fun headingFwd(): Pair<Float, Float> = fwdX to fwdZ

    fun stop() {
        runCatching { controllerProvider.stop() }
        runCatching { hmdProvider.stop() }
        started = false
    }

    /** 每帧推进（dt 秒），返回世界根节点应有的位移（把世界搬到玩家脚下） */
    fun tick(dt: Float): Vector3 {
        val magnitude = hypot(stickX, stickY)
        if (magnitude > 0.12f) {
            val clamped = magnitude.coerceAtMost(1f)
            val dirX = stickX / magnitude
            val dirY = stickY / magnitude
            // 右向 = cross(forward, up) = (-fz, 0, fx)
            val rightX = -fwdZ
            val rightZ = fwdX
            val wx = fwdX * dirY + rightX * dirX
            val wz = fwdZ * dirY + rightZ * dirX
            val step = speed * clamped * dt
            var nx = playerX + wx * step
            var nz = playerZ + wz * step
            if (!TerrainMesh.walkable(nx, nz)) {
                if (TerrainMesh.walkable(nx, playerZ)) {
                    nz = playerZ
                } else if (TerrainMesh.walkable(playerX, nz)) {
                    nx = playerX
                } else {
                    nx = playerX
                    nz = playerZ
                }
            }
            playerX = nx
            playerZ = nz
        }
        groundHeight = TerrainMesh.height(playerX, playerZ)
        return Vector3(-playerX, -groundHeight, -playerZ)
    }
}
