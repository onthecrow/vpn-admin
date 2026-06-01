package com.onthecrow.vpnadmin

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VPN Admin",
        state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
    ) {
        App()
    }
}
