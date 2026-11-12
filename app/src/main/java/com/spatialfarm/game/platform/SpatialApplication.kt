package com.spatialfarm.game.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialfarm.game.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
