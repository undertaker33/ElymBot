package com.astrbot.android.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.AstrBotApplication

@Composable
inline fun <reified VM : ViewModel> astrBotViewModel(): VM {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as AstrBotApplication).appContainer
    return viewModel(factory = appContainer.viewModelFactory)
}
