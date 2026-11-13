package com.spatialfarm.game.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.data.plantById
import com.spatialfarm.game.scene.VillageEntity
import com.spatialfarm.game.scene.family.ChildEntity
import com.spatialfarm.game.scene.neighbor.StealSpotEntity
import com.spatialfarm.game.scene.plot.FarmPlotEntity
import com.spatialfarm.game.scene.props.QuestBoardEntity
import com.spatialfarm.game.scene.props.ShopKind
import com.spatialfarm.game.scene.props.ShopPointEntity
import com.spatialfarm.game.window.WINDOW_ID_ALMANAC
import com.spatialfarm.game.window.WINDOW_ID_BUYER
import com.spatialfarm.game.window.WINDOW_ID_MAP
import com.spatialfarm.game.window.WINDOW_ID_QUESTS
import com.spatialfarm.game.window.WINDOW_ID_SEED_SHOP
import com.spatialfarm.game.window.WINDOW_ID_TOOLSHOP

private class SceneHolder {
    var village: VillageEntity? = null
    var menuEntity: Entity? = null
}

/**
 * 主场景：
 * - 村子里的**房子就是店**（点梅姨摊=买种子/胖婶食堂=卖菜/锤叔作坊=工具/委托板=接任务）
 * - HUD 改为**呼出式**：默认只有一个小「☰ 菜单」按钮，点开才有面板，面板上 ✕ 收起
 * - 点田垄弹动作菜单（选种/浇水/收获）
 */
@Composable
fun HomeStage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val sceneHolder = remember { SceneHolder() }
    var plotMenu by remember { mutableStateOf<FarmPlotEntity?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        GameState.onScarecrowBought = {
            sceneHolder.village?.placeScarecrow()
        }
        onDispose {
            GameState.onScarecrowBought = null
            sceneHolder.village?.destroy(true)
            sceneHolder.village = null
        }
    }

    SpatialView(
        modifier =
            Modifier.pointerInput(Unit) {
                detectSpatialTapGesture(context) { tap ->
                    var e = tap.targetEntity
                    var handled = false
                    while (e != null) {
                        if (e is FarmPlotEntity) {
                            plotMenu = e
                            val idx = sceneHolder.village?.plots?.indexOf(e) ?: -1
                            if (idx >= 0) {
                                val anchor = sceneHolder.village?.plotAnchor(idx)
                                if (anchor != null) {
                                    sceneHolder.menuEntity?.components?.get(TransformComponent::class.java)
                                        ?.setPosition(anchor)
                                }
                            }
                            handled = true
                            break
                        }
                        if (e is StealSpotEntity) {
                            e.onTap()
                            handled = true
                            break
                        }
                        if (e is ShopPointEntity) {
                            when (e.shopKind()) {
                                ShopKind.SEED -> navigator.openWindowContainer(WINDOW_ID_SEED_SHOP)
                                ShopKind.BUYER -> navigator.openWindowContainer(WINDOW_ID_BUYER)
                                ShopKind.TOOL -> navigator.openWindowContainer(WINDOW_ID_TOOLSHOP)
                            }
                            handled = true
                            break
                        }
                        if (e is QuestBoardEntity) {
                            navigator.openWindowContainer(WINDOW_ID_QUESTS)
                            handled = true
                            break
                        }
                        if (e is ChildEntity) {
                            e.onTap()
                            handled = true
                            break
                        }
                        e = e.getParent()
                    }
                    if (!handled) plotMenu = null
                }
            },
        initial = { content, attachments ->
            val village = sceneHolder.village ?: VillageEntity().also { sceneHolder.village = it }
            content.addEntity(village)

            fun place(id: String, pos: Vector3) {
                attachments.entity(id = id)?.apply {
                    components[TransformComponent::class.java]?.setPosition(pos)
                    content.addEntity(this)
                }
            }
            place("menu_btn", Vector3(0.5f, 1.2f, -0.85f))
            place("panel", Vector3(0f, 1.5f, -1.4f))
            place("plot_menu", Vector3(0f, -10f, 0f))
            // 店铺招牌（房子上方的功能提示）
            place("sign_seed", Vector3(-2.6f, 2.75f, -6.2f))
            place("sign_buy", Vector3(0f, 3.45f, -6.5f))
            place("sign_tool", Vector3(2.8f, 3.0f, -6.2f))

            sceneHolder.menuEntity = attachments.entity(id = "plot_menu")
        },
        attachments = {
            // ── 呼出按钮（常驻小按钮）────────────────────────
            AttachmentPanel(id = "menu_btn") {
                Button(onClick = { menuOpen = true }) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(text = "☰ 菜单", fontSize = 30.sp)
                    }
                }
            }

            // ── 主面板（呼出显示，✕/按钮收起）────────────────
            AttachmentPanel(id = "panel") {
                if (!menuOpen) {
                    Box(modifier = Modifier.size(1.dp))
                } else {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .backgroundMaterial(true, Material.Regular),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
                        ) {
                            // 标题行 + 关闭
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🌱 小小田园 · 清风岛",
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.titleMedium.copy(fontSize = 34.sp),
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Button(onClick = { menuOpen = false }) {
                                    Text(text = "✕", fontSize = 26.sp)
                                }
                            }

                            // 钱包 / 篮子
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(
                                    text = "💰 ¥${GameState.wallet}",
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.titleMedium.copy(fontSize = 32.sp),
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                Text(
                                    text = "🧺 ${GameState.produceTotal()}",
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.titleMedium.copy(fontSize = 32.sp),
                                )
                            }

                            // 田垄状态栏
                            run { GameState.plotRevision }
                            val v = sceneHolder.village
                            if (v != null) {
                                val line = v.plots.take(GameState.plotsOwned).mapIndexed { i, plot ->
                                    when (val st = plot.cropState()) {
                                        0 -> "田${i + 1}·空"
                                        5 -> "田${i + 1}·✅"
                                        else -> "田${i + 1}·🌱$st/4"
                                    }
                                }.joinToString(" ")
                                Text(
                                    text = "🌾 $line",
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 22.sp),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }

                            // 功能按钮（店铺已移到世界里：点房子开店）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 10.dp),
                            ) {
                                Button(onClick = { navigator.openWindowContainer(WINDOW_ID_MAP) }) {
                                    Text(text = "🗺️ 地图", fontSize = 26.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { navigator.openWindowContainer(WINDOW_ID_ALMANAC) }) {
                                    Text(text = "📕 图鉴", fontSize = 26.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { navigator.openWindowContainer(WINDOW_ID_QUESTS) }) {
                                    Text(text = "📋 委托", fontSize = 26.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val plots = sceneHolder.village?.plots ?: return@Button
                                        val target =
                                            plots.firstOrNull { it.cropState() in 1..5 }
                                                ?: plots.firstOrNull()
                                        target?.onTap()
                                    }
                                ) {
                                    Text(text = "🌱 一键照料", fontSize = 24.sp)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                GameState.nextPlotPrice()?.let { price ->
                                    Button(
                                        onClick = {
                                            GameState.buyNextPlot()?.let { idx ->
                                                sceneHolder.village?.plots?.getOrNull(idx)?.unlock()
                                            }
                                        }
                                    ) {
                                        Text(text = "🪴 扩田 ¥$price", fontSize = 24.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { sceneHolder.village?.toggleDiorama() }) {
                                    Text(text = "🔭 沙盘", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                var childIdx by remember { mutableIntStateOf(0) }
                                Button(
                                    onClick = {
                                        val kids = sceneHolder.village?.children ?: return@Button
                                        if (kids.isNotEmpty()) {
                                            kids[childIdx % kids.size].onTap()
                                            childIdx++
                                        }
                                    }
                                ) {
                                    Text(text = "🧒 孩子", fontSize = 24.sp)
                                }
                            }

                            // 种子快捷栏（一键照料默认用选中的种子）
                            if (GameState.seeds.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text(
                                        text = "选种：",
                                        color = PicoTheme.colorScheme.labelSecondary,
                                        style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                    )
                                    for ((seedId, count) in GameState.seeds) {
                                        val def = plantById(seedId) ?: continue
                                        val selected = GameState.selectedSeedId == seedId
                                        Button(
                                            onClick = { GameState.selectSeed(seedId) },
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                        ) {
                                            Text(
                                                text = (if (selected) "✓" else "") + "${def.emoji}×$count",
                                                fontSize = 24.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // 新手引导
                            val guide = when {
                                sceneHolder.village?.plots?.any { it.cropState() == 5 } == true ->
                                    "👉 菜熟了！点田垄打开菜单收获 🧺"
                                sceneHolder.village?.plots?.any { it.cropState() in 1..4 } == true ->
                                    "👉 点田垄打开菜单浇水 💧"
                                GameState.seeds.isNotEmpty() &&
                                    sceneHolder.village?.plots?.any { it.cropState() == 0 } == true ->
                                    "👉 点田垄播种，或按「🌱 一键照料」"
                                GameState.produceTotal() > 0 ->
                                    "👉 去胖婶食堂卖菜，或接委托"
                                GameState.seeds.isEmpty() ->
                                    "👉 去梅姨杂货铺买种子（点房子）"
                                else -> null
                            }
                            if (guide != null) {
                                Text(
                                    text = guide,
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 24.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                            Text(
                                text = GameState.hint,
                                color = PicoTheme.colorScheme.labelSecondary,
                                style = PicoTheme.typography.bodySmall.copy(fontSize = 20.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            // ── 店铺招牌（点招牌=开店，抬头就能看到）──────────
            AttachmentPanel(id = "sign_seed") {
                ShopSign("🏪 梅姨·买种子") { navigator.openWindowContainer(WINDOW_ID_SEED_SHOP) }
            }
            AttachmentPanel(id = "sign_buy") {
                ShopSign("🍲 胖婶·卖蔬菜") { navigator.openWindowContainer(WINDOW_ID_BUYER) }
            }
            AttachmentPanel(id = "sign_tool") {
                ShopSign("🛠️ 锤叔·买工具") { navigator.openWindowContainer(WINDOW_ID_TOOLSHOP) }
            }

            // ── 田垄动作菜单 ────────────────────────────────
            AttachmentPanel(id = "plot_menu") {
                val target = plotMenu
                if (target == null) {
                    Box(modifier = Modifier.size(1.dp))
                } else {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .backgroundMaterial(true, Material.Regular),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            val st = target.cropState()
                            Text(
                                text = when {
                                    st == 0 -> "🌱 空地 · 选择种子"
                                    st == 5 -> "🧺 ${target.cropName() ?: "作物"} 成熟了！"
                                    else -> "🌿 ${target.cropName() ?: "作物"} 生长中 $st/4"
                                },
                                color = PicoTheme.colorScheme.labelPrimary,
                                style = PicoTheme.typography.titleMedium.copy(fontSize = 28.sp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            when {
                                st == 0 -> {
                                    if (GameState.seeds.isEmpty()) {
                                        Text(
                                            text = "没有种子了，去梅姨杂货铺买",
                                            color = PicoTheme.colorScheme.labelSecondary,
                                            style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                        )
                                    }
                                    for ((seedId, count) in GameState.seeds) {
                                        val def = plantById(seedId) ?: continue
                                        Button(
                                            onClick = {
                                                target.plantSeedById(seedId)
                                                plotMenu = null
                                            }
                                        ) {
                                            Text(text = "🌱 ${def.emoji}${def.name} ×$count", fontSize = 24.sp)
                                        }
                                        Spacer(modifier = Modifier.size(6.dp))
                                    }
                                }
                                st == 5 -> {
                                    Button(
                                        onClick = {
                                            target.tapHarvest()
                                            plotMenu = null
                                        }
                                    ) {
                                        Text(text = "🧺 收获", fontSize = 26.sp)
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            target.tapWater()
                                            plotMenu = null
                                        }
                                    ) {
                                        Text(text = "💧 浇水", fontSize = 26.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            Button(onClick = { plotMenu = null }) {
                                Text(text = "✕ 取消", fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ShopSign(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(text = label, fontSize = 104.sp)
        }
    }
}
