package com.spatialfarm.game

import androidx.compose.ui.unit.dp
import com.spatialfarm.game.content.HomeStage
import com.spatialfarm.game.window.AlmanacWindow
import com.spatialfarm.game.window.BuyerStandWindow
import com.spatialfarm.game.window.SeedShopWindow
import com.spatialfarm.game.window.WINDOW_ID_ALMANAC
import com.spatialfarm.game.window.WINDOW_ID_BUYER
import com.spatialfarm.game.window.WINDOW_ID_SEED_SHOP
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                HomeStage()
            }
        }

        // 窗口 A：梅姨的种子商店
        WindowContainer(
            id = WINDOW_ID_SEED_SHOP,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 900.dp, height = 620.dp),
        ) {
            SeedShopWindow()
        }

        // 窗口 B：胖婶的收购站
        WindowContainer(
            id = WINDOW_ID_BUYER,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 860.dp, height = 620.dp),
        ) {
            BuyerStandWindow()
        }

        // 窗口 C：植物图鉴
        WindowContainer(
            id = WINDOW_ID_ALMANAC,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 920.dp, height = 660.dp),
        ) {
            AlmanacWindow()
        }
    }
