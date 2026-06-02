package com.onthecrow.vpnadmin

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val icon = remember {
        useResource("icon.png") { BitmapPainter(loadImageBitmap(it)) }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "VPN Admin",
        icon = icon,
        state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
    ) {
        App()
    }
}
