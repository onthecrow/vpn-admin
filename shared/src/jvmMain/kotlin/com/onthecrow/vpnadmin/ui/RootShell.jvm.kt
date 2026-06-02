package com.onthecrow.vpnadmin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onthecrow.vpnadmin.nav.RootTab

@Composable
actual fun RootShell(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
            RootTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Text(tab.label.first().toString()) },
                    label = { Text(tab.label) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}
