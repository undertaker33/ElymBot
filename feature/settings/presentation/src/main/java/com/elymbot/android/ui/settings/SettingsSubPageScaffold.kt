package com.elymbot.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.app.SecondaryTopBarPlaceholder

@Composable
internal fun SettingsSubPageScaffold(
    @Suppress("UNUSED_PARAMETER")
    title: String,
    @Suppress("UNUSED_PARAMETER")
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MonochromeUi.pageBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SecondaryTopBarPlaceholder() },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MonochromeUi.pageBackground),
        ) {
            content(PaddingValues(0.dp))
        }
    }
}
