package com.spatialfarm.game.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator

/**
 * 窗口右上角关闭按钮（✕）。
 * 空间应用的窗口默认没有常驻关闭按钮，加上它才符合普通应用的关闭习惯。
 */
@Composable
fun WindowCloseButton(windowId: String) {
    val navigator = LocalSpatialNavigator.current
    Button(onClick = { navigator.closeWindowContainer(windowId) }) {
        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text = "✕", fontSize = 30.sp)
        }
    }
}
