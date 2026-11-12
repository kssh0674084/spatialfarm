package com.spatialfarm.game.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.PLANT_CATALOG

/** 窗口 C：植物图鉴 & 知识卡（科普灵魂） */
const val WINDOW_ID_ALMANAC = "almanac"

@Composable
fun AlmanacWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📕 小小田园植物图鉴",
                    style = PicoTheme.typography.titleLarge.copy(fontSize = 40.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                val unlocked = PLANT_CATALOG.count { GameState.isUnlocked(it.id) }
                Text(
                    text = "已解锁 $unlocked/${PLANT_CATALOG.size}",
                    style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                    color = PicoTheme.colorScheme.labelSecondary,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PLANT_CATALOG) { plant ->
                    val unlocked = GameState.isUnlocked(plant.id)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PicoTheme.colorScheme.fillSecondary)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = if (unlocked) plant.emoji else "❓",
                            fontSize = 46.sp,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (unlocked) plant.name else "？？？",
                                    style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                                    color = PicoTheme.colorScheme.labelPrimary,
                                )
                                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                                Text(
                                    text = "⭐".repeat(plant.difficulty),
                                    fontSize = 20.sp,
                                )
                            }
                            if (unlocked) {
                                Text(
                                    text = "${plant.season}季 · ${plant.growDays}天成熟 · 收购 ¥${plant.baseSellPrice}",
                                    style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                    color = PicoTheme.colorScheme.labelSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // 知识卡：科普灵魂
                                Text(
                                    text = "💡 ${plant.knowledge}",
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 24.sp),
                                    color = PicoTheme.colorScheme.labelPrimary,
                                )
                            } else {
                                Text(
                                    text = "收获一次${plant.name}即可解锁知识卡",
                                    style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                    color = PicoTheme.colorScheme.labelTertiary,
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
