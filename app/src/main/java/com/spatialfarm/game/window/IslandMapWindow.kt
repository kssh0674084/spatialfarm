package com.spatialfarm.game.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.scene.Locomotion
import com.spatialfarm.game.scene.TerrainMesh
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 窗口 F：清风岛地图（Canvas 绘制 + 实时玩家位置） */
const val WINDOW_ID_MAP = "island_map"

@Composable
fun IslandMapWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🗺️ 清风岛地图",
                    style = PicoTheme.typography.titleLarge.copy(fontSize = 34.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "💰 ¥${GameState.wallet}",
                    style = PicoTheme.typography.titleMedium.copy(fontSize = 32.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                WindowCloseButton(WINDOW_ID_MAP)
            }

            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp)) {
                // 地图画布
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFBEE3F2)),
                ) {
                    IslandCanvas(modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(18.dp))
                // 图例
                Column(modifier = Modifier.width(220.dp).padding(top = 6.dp)) {
                    Text(
                        text = "🧭 图例",
                        fontSize = 28.sp,
                        color = PicoTheme.colorScheme.labelPrimary,
                    )
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                    LegendLine("🔺", "你（实时位置）")
                    LegendLine("🏘️", "村庄 / 你的田")
                    LegendLine("🌊", "环海 / 湖泊")
                    LegendLine("🏔️", "远山天际线")
                    LegendLine("🌲", "森林丘陵")
                    Spacer(modifier = Modifier.padding(top = 10.dp))
                    Text(
                        text = "推左摇杆在岛上走动，地图红三角会跟着你移动。",
                        fontSize = 22.sp,
                        color = PicoTheme.colorScheme.labelSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendLine(emoji: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 22.sp, color = PicoTheme.colorScheme.labelSecondary)
    }
}

@Composable
private fun IslandCanvas(modifier: Modifier) {
    // 订阅玩家位置（Compose 状态，走动时自动重绘）
    val px = Locomotion.playerX
    val pz = Locomotion.playerZ
    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = side / 118f   // 世界半径 50m 留边

        fun mapX(wx: Float) = cx + wx * scale
        fun mapY(wz: Float) = cy + wz * scale

        // 海（背景已是海色，这里画深浅两层）
        drawCircle(
            color = Color(0xFF9ED4EA),
            radius = 58f * scale,
            center = Offset(cx, cy),
        )

        // 岛屿（草绿圆盘 + 沙滩环）
        drawCircle(
            color = Color(0xFFE8DDAA),
            radius = 51f * scale,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color(0xFF8CCB66),
            radius = 47f * scale,
            center = Offset(cx, cy),
        )

        // 湖泊
        drawCircle(
            color = Color(0xFF6FC2E3),
            radius = 10.5f * scale,
            center = Offset(mapX(-20f), mapY(16f)),
        )

        // 溪流
        val stream = TerrainMesh.streamSegments()
        val streamPath = Path()
        streamPath.moveTo(mapX(stream[0][0]), mapY(stream[0][1]))
        for (i in 1 until stream.size) {
            streamPath.lineTo(mapX(stream[i][0]), mapY(stream[i][1]))
        }
        drawPath(
            path = streamPath,
            color = Color(0xFF6FC2E3),
            style = Stroke(width = 2.6f * scale),
        )

        // 风车丘陵（高亮小丘）
        drawCircle(
            color = Color(0xFFB8DB7E),
            radius = 9f * scale,
            center = Offset(mapX(24f), mapY(-16f)),
        )

        // 远山（外圈灰点）
        for (i in 0 until 10) {
            val a = (i / 10f) * Math.PI.toFloat() * 2f + 0.2f
            val d = 54f * scale
            drawCircle(
                color = Color(0xFF93A0B8),
                radius = 2.6f * scale,
                center = Offset(cx + cos(a) * d, cy + sin(a) * d),
            )
        }

        // 村庄（中心方块群）
        drawRect(
            color = Color(0xFFD97B66),
            topLeft = Offset(mapX(-3.5f), mapY(-9f)),
            size = Size(7f * scale, 7.5f * scale),
        )
        // 田区块
        drawRect(
            color = Color(0xFFA9764D),
            topLeft = Offset(mapX(-4.5f), mapY(-1.5f)),
            size = Size(9f * scale, 7f * scale),
        )

        // 玩家标记（红色三角，指向视野朝向）
        val h = Locomotion.headingFwd()
        val angle = kotlin.math.atan2(h.second, h.first)  // 画布坐标与世界 XZ 同向（Y 向下 = +Z）
        val mx = mapX(px)
        val my = mapY(pz)
        val r = 3.2f * scale
        val tip = Offset(mx + cos(angle) * r, my + sin(angle) * r)
        val left = Offset(
            mx + cos(angle + 2.5f) * r * 0.8f,
            my + sin(angle + 2.5f) * r * 0.8f,
        )
        val right = Offset(
            mx + cos(angle - 2.5f) * r * 0.8f,
            my + sin(angle - 2.5f) * r * 0.8f,
        )
        val triangle = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
        drawPath(triangle, color = Color(0xFFE53935))
        drawCircle(color = Color.White, radius = r * 0.35f, center = Offset(mx, my))
    }
}
