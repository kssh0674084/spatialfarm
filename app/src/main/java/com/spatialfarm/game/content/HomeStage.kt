package com.spatialfarm.game.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.spatialfarm.game.data.GameState
import com.spatialfarm.game.scene.VillageEntity
import com.spatialfarm.game.window.WINDOW_ID_SEED_SHOP

/**
 * 主场景：把村口小院（旷野之息风格草原）挂进 Stage，
 * 并在田垄上方悬浮游戏标题面板。
 */
@Composable
fun HomeStage() {
    SpatialView(
        initial = { content, attachments ->
            val village = VillageEntity()
            content.addEntity(village)

            val titleAttachment = attachments.entity(id = "title")
            titleAttachment?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, 1.9f, -3.5f))
                }
                content.addEntity(this)
            }
        },
        attachments = {
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
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                    ) {
                        Text(
                            text = "🌱 小小田园",
                            color = PicoTheme.colorScheme.labelPrimary,
                            style = PicoTheme.typography.titleLarge.copy(fontSize = 72.sp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "清风岛 · 村口小院",
                            color = PicoTheme.colorScheme.labelSecondary,
                            style = PicoTheme.typography.bodyLarge.copy(fontSize = 36.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        // HUD：钱包 + 打开种子商店
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 20.dp),
                        ) {
                            Text(
                                text = "💰 ¥${GameState.wallet}",
                                color = PicoTheme.colorScheme.labelPrimary,
                                style = PicoTheme.typography.titleMedium.copy(fontSize = 40.sp),
                            )
                            Spacer(modifier = Modifier.width(24.dp))
                            Button(
                                onClick = { navigator.openWindowContainer(WINDOW_ID_SEED_SHOP) }
                            ) {
                                Text(text = "🏪 种子商店", fontSize = 32.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}
