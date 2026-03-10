package com.astrbot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.astrbot.android.ui.AstrBotApp
import com.astrbot.android.ui.theme.AstrBotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstrBotTheme {
                AstrBotApp()
            }
        }
    }
}
