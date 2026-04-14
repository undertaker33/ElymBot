package com.astrbot.android

import android.app.Application
import android.util.Log
import com.astrbot.android.di.AstrBotAppContainer

class AstrBotApplication : Application() {
    lateinit var appContainer: AstrBotAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate entered")
        appContainer = AstrBotAppContainer(this)
        appContainer.bootstrap()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate completed")
    }
}
