package com.astrbot.android

import android.app.Application
import com.astrbot.android.di.AstrBotAppContainer

class AstrBotApplication : Application() {
    lateinit var appContainer: AstrBotAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AstrBotAppContainer(this)
        appContainer.bootstrap()
    }
}
