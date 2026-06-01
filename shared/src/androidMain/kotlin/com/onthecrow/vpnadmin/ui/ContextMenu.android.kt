package com.onthecrow.vpnadmin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun ContextMenuWrapper(
    items: List<ContextMenuItem>,
    onPrimaryClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.combinedClickable(
            onClick = onPrimaryClick,
            onLongClick = { expanded = true },
        )
    ) {
        content()
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
