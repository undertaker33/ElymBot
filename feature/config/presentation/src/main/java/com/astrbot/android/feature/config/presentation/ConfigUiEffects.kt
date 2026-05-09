package com.astrbot.android.ui.config

import androidx.compose.foundation.lazy.LazyListState

suspend fun LazyListState.animateToItemWithAppMotion(index: Int) {
    animateScrollToItem(index)
}
