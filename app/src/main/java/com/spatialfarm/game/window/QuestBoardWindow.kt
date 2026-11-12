package com.spatialfarm.game.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.spatialfarm.game.data.Quests
import com.spatialfarm.game.data.plantById

/** 窗口 E：广场委托板 */
const val WINDOW_ID_QUESTS = "quest_board"

@Composable
fun QuestBoardWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📋 广场委托板",
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

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Quests.today.toList()) { quest ->
                    val plant = plantById(quest.plantId)
                    val owned = GameState.produce[quest.plantId] ?: 0
                    val canDeliver = !quest.done && owned >= quest.count
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (quest.done) PicoTheme.colorScheme.fillSecondary.copy(alpha = 0.5f)
                                    else PicoTheme.colorScheme.fillSecondary
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = if (quest.done) "✅" else "📜", fontSize = 40.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${quest.npc}想要 ${quest.count} 个${plant?.emoji ?: ""}${plant?.name ?: quest.plantId}",
                                style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                                color = PicoTheme.colorScheme.labelPrimary,
                            )
                            Text(
                                text = if (quest.done) "已完成"
                                else "奖励 ¥${quest.reward} · 篮中有 $owned 个",
                                style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                color = PicoTheme.colorScheme.labelSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { Quests.deliver(quest) },
                            enabled = canDeliver,
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(text = if (quest.done) "已交付" else "交付", fontSize = 24.sp)
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "💡 委托每天刷新。种得多、卖得巧，不如接得准。",
                        style = PicoTheme.typography.bodyMedium.copy(fontSize = 24.sp),
                        color = PicoTheme.colorScheme.labelSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
