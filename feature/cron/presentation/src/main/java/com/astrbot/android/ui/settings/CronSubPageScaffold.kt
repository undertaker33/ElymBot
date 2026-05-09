package com.astrbot.android.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.app.SecondaryTopBarSpec

@Composable
internal fun CronSubPageScaffold(
    route: String = "",
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    RegisterSecondaryTopBar(
        route = route,
        spec = SecondaryTopBarSpec.SubPage(
            title = title,
            onBack = onBack,
        ),
    )
    Scaffold(
        topBar = { SecondaryTopBarPlaceholder() },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
        content = content,
    )
}
