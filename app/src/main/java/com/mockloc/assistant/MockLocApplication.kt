package com.mockloc.assistant

import android.app.Application
import com.mockloc.assistant.data.map.MapTileSources

class MockLocApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapTileSources.initialize(this)
    }
}
