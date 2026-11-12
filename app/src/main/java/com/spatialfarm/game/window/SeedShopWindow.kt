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
import androidx.compose.foundation.layout.size
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
import com.spatialfarm.game.data.PLANT_CATALOG
import com.spatialfarm.game.data.PlantDef

/**
 * 窗口 A：梅姨的种子商店（多窗口布局成员）。
 * 由 navigator.openWindowContainer(WINDOW_ID_SEED_SHOP) 打开。
 */
const val WINDOW_ID_SEED_SHOP = "seed_shop"

@Composable
fun SeedShopWindow() {
    PicoTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：店名 + 钱包
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🏪 梅姨的种子铺",
                    style = PicoTheme.typography.titleLarge.copy(fontSize = 40.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "💰 ¥${GameState.wallet}",
                    style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                WindowCloseButton(WINDOW_ID_SEED_SHOP)
            }

            // 种子列表
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PLANT_CATALOG) { plant ->
                    SeedRow(plant)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SeedRow(plant: PlantDef) {
    val owned = GameState.seedCount(plant.id)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PicoTheme.colorScheme.fillSecondary)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = plant.emoji, fontSize = 44.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plant.name,
                    style = PicoTheme.typography.titleMedium.copy(fontSize = 30.sp),
                    color = PicoTheme.colorScheme.labelPrimary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "持有 $owned",
                    style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                    color = PicoTheme.colorScheme.labelTertiary,
                )
            }
            Text(
                text = "⭐".repeat(plant.difficulty) + "  ${plant.season}季 · ${plant.growDays}天成熟 · 卖 ¥${plant.baseSellPrice}",
                style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                color = PicoTheme.colorScheme.labelSecondary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(onClick = { GameState.buySeed(plant) }) {
            Box(modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(text = "¥${plant.seedPrice}", fontSize = 26.sp)
            }
        }
    }
}
