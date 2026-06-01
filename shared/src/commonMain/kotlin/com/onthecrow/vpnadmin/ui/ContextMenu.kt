package com.onthecrow.vpnadmin.ui

import androidx.compose.runtime.Composable

data class ContextMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Wraps content with a platform-appropriate context menu trigger:
 *   - Desktop: right mouse click
 *   - Android: long press
 * A primary click (left mouse / tap) invokes [onPrimaryClick].
 */
@Composable
expect fun ContextMenuWrapper(
    items: List<ContextMenuItem>,
    onPrimaryClick: () -> Unit,
    content: @Composable () -> Unit,
)
