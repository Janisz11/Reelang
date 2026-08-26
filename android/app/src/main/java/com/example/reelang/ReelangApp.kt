package com.example.reelang

import android.app.Application
import com.example.reelang.events.EventTracker

class ReelangApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventTracker.init(this)
    }
}
