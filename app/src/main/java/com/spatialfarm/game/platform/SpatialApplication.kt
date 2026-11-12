package com.spatialfarm.game.platform

import android.app.Application
import com.spatialfarm.game.data.GameState
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialfarm.game.mainApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 绑定存档并恢复进度（必须在场景构建前）
        GameState.init(this)
        launch(::mainApp)

        // 周期自动存档（3 秒）
        MainScope().launch {
            while (true) {
                delay(3_000)
                GameState.save()
            }
        }
    }
}
