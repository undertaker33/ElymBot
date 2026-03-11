package com.astrbot.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MonochromeUi {
    val pageBackground = Color(0xFFF3F3F1)
    val cardBackground = Color(0xFFEDEDEC)
    val cardAltBackground = Color(0xFFE4E4E1)
    val mutedSurface = Color(0xFFE1E1DE)
    val textPrimary = Color(0xFF111111)
    val textSecondary = Color(0xFF666666)
    val border = Color(0xFFD6D6D2)
    val strong = Color(0xFF151515)
    val strongAlt = Color(0xFF2A2A2A)
    val radiusCard = RoundedCornerShape(26.dp)
    val radiusInput = RoundedCornerShape(24.dp)
}

@Composable
fun monochromeOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MonochromeUi.textPrimary,
    unfocusedBorderColor = MonochromeUi.border,
    disabledBorderColor = MonochromeUi.border.copy(alpha = 0.6f),
    focusedLabelColor = MonochromeUi.textPrimary,
    unfocusedLabelColor = MonochromeUi.textSecondary,
    focusedTextColor = MonochromeUi.textPrimary,
    unfocusedTextColor = MonochromeUi.textPrimary,
    cursorColor = MonochromeUi.textPrimary,
    focusedLeadingIconColor = MonochromeUi.textSecondary,
    unfocusedLeadingIconColor = MonochromeUi.textSecondary,
    focusedPlaceholderColor = MonochromeUi.textSecondary,
    unfocusedPlaceholderColor = MonochromeUi.textSecondary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
)

@Composable
fun monochromeSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MonochromeUi.strong,
    checkedBorderColor = MonochromeUi.strong,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFD4D4D0),
    uncheckedBorderColor = Color(0xFFD4D4D0),
)
