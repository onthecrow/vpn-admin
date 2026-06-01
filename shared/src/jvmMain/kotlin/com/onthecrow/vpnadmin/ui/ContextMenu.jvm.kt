package com.onthecrow.vpnadmin.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem as DesktopContextMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ContextMenuWrapper(
    items: List<ContextMenuItem>,
    onPrimaryClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ContextMenuArea(
        items = { items.map { item -> DesktopContextMenuItem(item.label, item.onClick) } },
    ) {
        Box(modifier = Modifier.clickable(onClick = onPrimaryClick)) {
            content()
        }
    }
}
