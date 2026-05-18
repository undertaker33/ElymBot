package com.elymbot.android

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elymbot.android.di.AppBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ElymBotApplication : Application(), Configuration.Provider {
    @Inject
    internal lateinit var appBootstrapper: AppBootstrapper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i("ElymBotRuntime", "ElymBotApplication.onCreate entered")
        appBootstrapper.bootstrap()
        Log.i("ElymBotRuntime", "ElymBotApplication.onCreate completed")
    }
}
