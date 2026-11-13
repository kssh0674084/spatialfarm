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
import com.spatialfarm.game.window.WINDOW_ID_ALMANAC
import com.spatialfarm.game.window.WINDOW_ID_BUYER
import com.spatialfarm.game.window.WINDOW_ID_QUESTS
import com.spatialfarm.game.window.WINDOW_ID_SEED_SHOP
import com.spatialfarm.game.window.WINDOW_ID_TOOLSHOP

/**
 * 主场景：村口小院 + 标题/HUD 面板。
 * 点按远方 3D 田垄 → 播种 / 浇水 / 收获（向上寻找 FarmPlotEntity）。
 */
private class SceneHolder {
    var village: VillageEntity? = null
    var menuEntity: Entity? = null
}

@Composable
fun HomeStage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    // 场景生命周期管理：离开组合时销毁村庄，防止重复加载导致内存膨胀
    val sceneHolder = remember { SceneHolder() }
    var plotMenu by remember { mutableStateOf<FarmPlotEntity?>(null) }
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
    // 演示模式：应用内自动表演（录屏用，无需外部自动化）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!GameState.demoMode) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val nav = navigator
        val village = sceneHolder.village ?: return@LaunchedEffect
        // 1) 浇水两次（水花特效特写）
        village.plots.firstOrNull { it.cropState() in 1..4 }?.let {
            it.onTap(); kotlinx.coroutines.delay(1800); it.onTap()
        }
        kotlinx.coroutines.delay(2200)
        // 2) 成熟收获（闪光）
        village.plots.firstOrNull { it.cropState() == 5 }?.let { it.onTap() }
        kotlinx.coroutines.delay(2200)
        // 3) 卖菜窗口 + 自动全卖
        nav.openWindowContainer(WINDOW_ID_BUYER)
        kotlinx.coroutines.delay(2600)
        GameState.sellAll()
        kotlinx.coroutines.delay(1800)
        // 4) 图鉴知识卡
        nav.openWindowContainer(WINDOW_ID_ALMANAC)
        kotlinx.coroutines.delay(2800)
        // 5) 沙盘小人国
        village.toggleDiorama()
        kotlinx.coroutines.delay(3400)
        village.toggleDiorama()
        kotlinx.coroutines.delay(1600)
        // 6) 孩子帮忙收尾
        village.children.firstOrNull()?.onTap()
    }
    SpatialView(
        modifier =
            Modifier.pointerInput(Unit) {
                detectSpatialTapGesture(context) { tap ->
                    var e = tap.targetEntity
                    var handled = false
                    while (e != null) {
                        if (e is FarmPlotEntity) {
                            // 选择性交互：先弹动作菜单，由玩家决定做什么
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
                    if (!handled) plotMenu = null  // 点空白处收起菜单
                }
            },
        initial = { content, attachments ->
            val village = sceneHolder.village ?: VillageEntity().also { sceneHolder.village = it }
            content.addEntity(village)

            val menuAttachment = attachments.entity(id = "plot_menu")
            menuAttachment?.apply {
                components[TransformComponent::class.java]?.setPosition(Vector3(0f, -10f, 0f))
                content.addEntity(this)
            }
            sceneHolder.menuEntity = menuAttachment

            val titleAttachment = attachments.entity(id = "title")
            titleAttachment?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, 1.9f, -3.5f))
                }
                content.addEntity(this)
            }
        },
        attachments = {
            // 田垄动作菜单（点田垄弹出：先选择，再执行）
            AttachmentPanel(id = "plot_menu") {
                val target = plotMenu
                if (target == null) {
                    Box(modifier = Modifier.size(1.dp))
                } else {
                    Box(
                        modifier = Modifier
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
                                            text = "没有种子了，去「🏪 种子」买",
                                            color = PicoTheme.colorScheme.labelSecondary,
                                            style = PicoTheme.typography.bodySmall.copy(fontSize = 22.sp),
                                        )
                                    }
                                    for ((seedId, count) in GameState.seeds) {
                                        val def = plantById(seedId) ?: continue
                                        Button(onClick = {
                                            target.plantSeedById(seedId)
                                            plotMenu = null
                                        }) {
                                            Text(text = "🌱 ${def.emoji}${def.name} ×$count", fontSize = 24.sp)
                                        }
                                        Spacer(modifier = Modifier.size(6.dp))
                                    }
                                }
                                st == 5 -> {
                                    Button(onClick = {
                                        target.tapHarvest()
                                        plotMenu = null
                                    }) {
                                        Text(text = "🧺 收获", fontSize = 26.sp)
                                    }
                                }
                                else -> {
                                    Button(onClick = {
                                        target.tapWater()
                                        plotMenu = null
                                    }) {
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

            AttachmentPanel(id = "title") {
                val navigator = LocalSpatialNavigator.current
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .backgroundMaterial(true, Material.Regular),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                    ) {
                        Text(
                            text = "🌱 小小田园",
                            color = PicoTheme.colorScheme.labelPrimary,
                            style = PicoTheme.typography.titleLarge.copy(fontSize = 64.sp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "清风岛 · 村口小院",
                            color = PicoTheme.colorScheme.labelSecondary,
                            style = PicoTheme.typography.bodyLarge.copy(fontSize = 30.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )

                        // 第一行：钱包 / 收成
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            Text(
                                text = "💰 ¥${GameState.wallet}",
                                color = PicoTheme.colorScheme.labelPrimary,
                                style = PicoTheme.typography.titleMedium.copy(fontSize = 36.sp),
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = "🧺 ${GameState.produceTotal()}",
                                color = PicoTheme.colorScheme.labelPrimary,
                                style = PicoTheme.typography.titleMedium.copy(fontSize = 36.sp),
                            )
                        }

                        // 第二行：功能窗口
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_SEED_SHOP) }
                            ) {
                                Text(text = "🏪 种子", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_BUYER) }
                            ) {
                                Text(text = "🧺 卖菜", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_ALMANAC) }
                            ) {
                                Text(text = "📕 图鉴", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_TOOLSHOP) }
                            ) {
                                Text(text = "🛠️ 工具", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_QUESTS) }
                            ) {
                                Text(text = "📋 委托", fontSize = 28.sp)
                            }
                        }

                        // 第三行：田地操作
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            // 一键照料：有作物→浇水/收获，空地→播种（点按困难时的无障碍替代）
                            Button(
                                onClick = {
                                    val plots = sceneHolder.village?.plots ?: return@Button
                                    val target =
                                        plots.firstOrNull { it.cropState() in 1..5 }
                                            ?: plots.firstOrNull()
                                    target?.onTap()
                                }
                            ) {
                                Text(text = "🌱 一键照料", fontSize = 26.sp)
                            }
                            // 扩田（价格阶梯，买下后荒地变良田）
                            GameState.nextPlotPrice()?.let { price ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        GameState.buyNextPlot()?.let { idx ->
                                            sceneHolder.village?.plots?.getOrNull(idx)?.unlock()
                                        }
                                    }
                                ) {
                                    Text(text = "🪴 扩田 ¥$price", fontSize = 26.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { sceneHolder.village?.toggleDiorama() }
                            ) {
                                Text(text = "🔭 沙盘", fontSize = 26.sp)
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
                                Text(text = "🧒 孩子", fontSize = 26.sp)
                            }
                        }

                        // 田垄状态栏（一眼看清田里有什么）
                        run {
                            GameState.plotRevision // 订阅刷新
                            val v = sceneHolder.village
                            if (v != null) {
                                val line = v.plots.take(GameState.plotsOwned).mapIndexed { i, plot ->
                                    when (val st = plot.cropState()) {
                                        0 -> "田${i + 1}·空地"
                                        5 -> "田${i + 1}·✅成熟"
                                        else -> "田${i + 1}·🌱${st}/4"
                                    }
                                }.joinToString("  ")
                                Text(
                                    text = "🌾 $line",
                                    color = PicoTheme.colorScheme.labelPrimary,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 24.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }

                        // 新手引导线（按存档状态自动推进，田地状态优先）
                        run {
                            GameState.plotRevision // 订阅田垄状态刷新
                        }
                        val guide = when {
                            sceneHolder.village?.plots?.any { it.cropState() == 5 } == true ->
                                "👉 菜熟了！点田垄打开菜单收获 🧺"
                            sceneHolder.village?.plots?.any { it.cropState() in 1..4 } == true ->
                                "👉 点田垄打开菜单浇水 💧，长得更快、品质更好"
                            GameState.seeds.isNotEmpty() &&
                                sceneHolder.village?.plots?.any { it.cropState() == 0 } == true ->
                                "👉 点田垄打开菜单播种，或直接按「🌱 一键照料」"
                            GameState.produceTotal() > 0 ->
                                "👉 收成在手！去「🧺 卖菜」换钱，或接「📋 委托」赚更多"
                            GameState.seeds.isEmpty() ->
                                "👉 第一步：点「🏪 种子」，买一包生菜种子（¥50）"
                            else ->
                                null
                        }
                        if (guide != null) {
                            Text(
                                text = guide,
                                color = PicoTheme.colorScheme.labelPrimary,
                                style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }

                        // 种子快捷栏：点选种子 → 点田垄播种
                        if (GameState.seeds.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 14.dp),
                            ) {
                                Text(
                                    text = "选种：",
                                    color = PicoTheme.colorScheme.labelSecondary,
                                    style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp),
                                )
                                for ((seedId, count) in GameState.seeds) {
                                    val def = plantById(seedId) ?: continue
                                    val selected = GameState.selectedSeedId == seedId
                                    Button(
                                        onClick = { GameState.selectSeed(seedId) },
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    ) {
                                        Text(
                                            text = (if (selected) "✓" else "") +
                                                "${def.emoji}×$count",
                                            fontSize = 28.sp,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "还没有种子，先去商店买吧 →",
                                color = PicoTheme.colorScheme.labelSecondary,
                                style = PicoTheme.typography.bodyMedium.copy(fontSize = 26.sp),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }

                        // 操作提示
                        Text(
                            text = GameState.hint,
                            color = PicoTheme.colorScheme.labelSecondary,
                            style = PicoTheme.typography.bodySmall.copy(fontSize = 24.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Spacer(modifier = Modifier.size(2.dp))
                    }
                }
            }
        },
    )
}
