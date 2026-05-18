package com.elymbot.android.ui.bot

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elymbot.android.core.ui.R
import com.elymbot.android.ui.app.MonochromeUi

@Composable
fun SelectionField(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedId, options) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: stringResource(R.string.common_not_selected)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MonochromeUi.textSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelect(option.first)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun CatalogToggleHeader(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSelectLeft, contentPadding = PaddingValues(0.dp)) {
            Text(
                leftLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leftSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (leftSelected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
            )
        }
        Text("|", style = MaterialTheme.typography.titleMedium, color = MonochromeUi.textSecondary)
        TextButton(onClick = onSelectRight, contentPadding = PaddingValues(0.dp)) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leftSelected) FontWeight.Medium else FontWeight.Bold,
                color = if (leftSelected) MonochromeUi.textSecondary else MonochromeUi.textPrimary,
            )
        }
    }
}

@Composable
fun ScrollableAssistChipRow(
    items: List<String>,
    selectedItem: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onSelect(item) },
                label = { Text(item) },
                leadingIcon = if (selectedItem == item) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedItem == item) MonochromeUi.chipSelectedBackground else MonochromeUi.chipBackground,
                    labelColor = if (selectedItem == item) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    leadingIconContentColor = MonochromeUi.textPrimary,
                ),
            )
        }
    }
}
