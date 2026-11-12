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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.plantById

/** 窗口 B：胖婶的收购站（卖菜换钱） */
const val WINDOW_ID_BUYER = "buyer_stand"

@Composable
fun BuyerStandWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🧺 胖婶的收购站",
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

            if (GameState.produce.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🧺", fontSize = 90.sp)
                        Text(
                            text = "篮子空空的\n去田里种点菜吧",
                            color = PicoTheme.colorScheme.labelSecondary,
                            style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(GameState.produce.keys.toList()) { id ->
                        val def = plantById(id) ?: return@items
                        val count = GameState.produce[id] ?: 0
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PicoTheme.colorScheme.fillSecondary)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = def.emoji, fontSize = 44.sp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${def.name} ×$count",
                                    style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                                    color = PicoTheme.colorScheme.labelPrimary,
                                )
                                Text(
                                    text = "收购价 ¥${def.baseSellPrice}/个",
                                    style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                    color = PicoTheme.colorScheme.labelSecondary,
                                )
                            }
                            Button(
                                onClick = {
                                    val income = GameState.sellProduce(id, 1)
                                    if (income > 0) GameState.postHint("卖出 ${def.name} ×1，+¥$income")
                                }
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(text = "卖 1 个", fontSize = 24.sp)
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val total = GameState.sellAll()
                                if (total > 0) GameState.postHint("全部卖出，总收入 +¥$total 💰")
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text(
                                text = "全部卖出（¥${GameState.produce.entries.sumOf { (plantById(it.key)?.baseSellPrice ?: 0) * it.value }}）",
                                fontSize = 26.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
