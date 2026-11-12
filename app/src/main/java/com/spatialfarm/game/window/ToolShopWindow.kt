package com.spatialfarm.game.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.spatialfarm.game.data.GameState

/** 窗口 D：锤叔的工具铺 */
const val WINDOW_ID_TOOLSHOP = "tool_shop"

@Composable
fun ToolShopWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🛠️ 锤叔的工具铺",
                    style = PicoTheme.typography.titleLarge.copy(fontSize = 40.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "💰 ¥${GameState.wallet}",
                    style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ToolRow(
                    emoji = "💧",
                    name = "洒水喷头",
                    desc = "浇水品质 +30%（原 +20%），生长提速 +20%（原 +12%）",
                    priceText = if (GameState.hasSprinkler) "已拥有 ✅" else "¥800",
                    enabled = !GameState.hasSprinkler,
                    onBuy = { GameState.buySprinkler() },
                )
                ToolRow(
                    emoji = "🌱",
                    name = "肥料 ×5",
                    desc = "浇水时自动施肥：额外 +25% 生长进度；当前库存：${GameState.fertilizer}",
                    priceText = "¥200",
                    enabled = true,
                    onBuy = { GameState.buyFertilizer() },
                )
                ToolRow(
                    emoji = "🎃",
                    name = "稻草人",
                    desc = "立在田边吓唬老王：偷菜成功率 95% → 35%",
                    priceText = if (GameState.hasScarecrow) "已拥有 ✅" else "¥500",
                    enabled = !GameState.hasScarecrow,
                    onBuy = {
                        if (GameState.buyScarecrow()) GameState.onScarecrowBought?.invoke()
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 锤叔说：工具和土地一样，越早投资，回本越快。",
                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 24.sp),
                    color = PicoTheme.colorScheme.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun ToolRow(
    emoji: String,
    name: String,
    desc: String,
    priceText: String,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PicoTheme.colorScheme.fillSecondary)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 44.sp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                color = PicoTheme.colorScheme.labelPrimary,
            )
            Text(
                text = desc,
                style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                color = PicoTheme.colorScheme.labelSecondary,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(onClick = onBuy, enabled = enabled) {
            Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(text = priceText, fontSize = 24.sp)
            }
        }
    }
}
