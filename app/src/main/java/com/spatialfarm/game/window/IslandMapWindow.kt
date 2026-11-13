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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.spatialfarm.game.data.GameState

/** 窗口 F：清风岛地图（导航 + 各家庭介绍，点击地点直接前往） */
const val WINDOW_ID_MAP = "island_map"

@Composable
fun IslandMapWindow() {
    val navigator = LocalSpatialNavigator.current
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🗺️ 清风岛地图",
                    style = PicoTheme.typography.titleLarge.copy(fontSize = 40.sp),
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

            // 地图拼图（文字沙盘）
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "🌲🌲        小镇主街        🌲🌲",
                    textAlign = TextAlign.Center,
                    fontSize = 26.sp,
                    color = PicoTheme.colorScheme.labelSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapPlace("🏪", "梅姨杂货铺", "买种子") {
                        navigator.openWindowContainer(WINDOW_ID_SEED_SHOP)
                    }
                    MapPlace("🍲", "胖婶食堂", "卖蔬菜") {
                        navigator.openWindowContainer(WINDOW_ID_BUYER)
                    }
                    MapPlace("🛠️", "锤叔作坊", "买工具") {
                        navigator.openWindowContainer(WINDOW_ID_TOOLSHOP)
                    }
                }
                Text(
                    text = "⛲ 广场（委托板 · 水井）        🚢 码头（海叔的船）",
                    textAlign = TextAlign.Center,
                    fontSize = 26.sp,
                    color = PicoTheme.colorScheme.labelSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapPlace("🏡", "你的家", "菜园和田地") {
                        navigator.closeWindowContainer(WINDOW_ID_MAP)
                    }
                    MapPlace("😴", "老王家", "小心偷菜!") {
                        navigator.closeWindowContainer(WINDOW_ID_MAP)
                    }
                    MapPlace("🌾", "你的田", "${GameState.plotsOwned} 块") {
                        navigator.closeWindowContainer(WINDOW_ID_MAP)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "💡 点地图上的地点就能去；游戏中也可以直接点村庄里的房子开店。",
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    color = PicoTheme.colorScheme.labelSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MapPlace(emoji: String, name: String, sub: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Box(
            modifier =
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PicoTheme.colorScheme.fillSecondary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = emoji, fontSize = 34.sp)
                Text(
                    text = name,
                    fontSize = 22.sp,
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Text(
                    text = sub,
                    fontSize = 18.sp,
                    color = PicoTheme.colorScheme.labelSecondary,
                )
            }
        }
    }
}
