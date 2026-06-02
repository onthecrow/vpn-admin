package com.onthecrow.vpnadmin.ui

import androidx.compose.runtime.Composable
import com.onthecrow.vpnadmin.nav.RootTab

/**
 * Platform-specific root chrome that exposes a tab switcher:
 *   - Desktop: a NavigationRail on the left
 *   - Android: a ModalNavigationDrawer with a hamburger button
 */
@Composable
expect fun RootShell(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    content: @Composable () -> Unit,
)
