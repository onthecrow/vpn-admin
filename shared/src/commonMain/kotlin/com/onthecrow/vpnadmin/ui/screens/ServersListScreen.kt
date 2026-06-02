package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.data.newDocId
import com.onthecrow.vpnadmin.threexui.ServerStatus
import com.onthecrow.vpnadmin.threexui.ServerStatusTracker
import com.onthecrow.vpnadmin.ui.ContextMenuItem
import com.onthecrow.vpnadmin.ui.ContextMenuWrapper
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersListScreen(
    repo: ServerRepository,
    tracker: ServerStatusTracker,
    onOpenDetail: (id: String) -> Unit,
    onEditServer: (id: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var loadError by remember { mutableStateOf<String?>(null) }
    val servers by remember {
        repo.observeAll().catch { e ->
            loadError = e.message ?: e::class.simpleName
            emit(emptyList())
        }
    }.collectAsState(initial = null)

    var pendingDelete by remember { mutableStateOf<Server?>(null) }
    var lastCheckedKey by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(servers) {
        val list = servers ?: return@LaunchedEffect
        val key = list.joinToString { it.id }.hashCode()
        if (key != lastCheckedKey) {
            lastCheckedKey = key
            tracker.checkAll(list)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                actions = {
                    TextButton(onClick = { servers?.let(tracker::checkAll) }) {
                        Text("Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditServer(null) }) { Text("+") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loadError != null -> ErrorBox(loadError!!)
                servers == null -> LoadingBox()
                servers!!.isEmpty() -> EmptyBox("No servers yet. Tap + to add one.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(servers!!, key = { it.id }) { server ->
                        ServerRow(
                            server = server,
                            status = tracker.status(server.id),
                            onOpenDetail = { onOpenDetail(server.id) },
                            onEdit = { onEditServer(server.id) },
                            onCopyUrl = {
                                clipboard.setText(AnnotatedString(server.panelBaseUrl))
                                scope.launch { snackbar.showSnackbar("Copied URL") }
                            },
                            onClone = {
                                scope.launch {
                                    runCatching {
                                        repo.create(
                                            server.copy(
                                                id = newDocId(),
                                                name = server.name + " (copy)",
                                            )
                                        )
                                    }.onFailure {
                                        snackbar.showSnackbar("Clone failed: ${it.message}")
                                    }
                                }
                            },
                            onDelete = { pendingDelete = server },
                            onStatusClick = {
                                when (val s = tracker.status(server.id)) {
                                    is ServerStatus.Failed ->
                                        scope.launch { snackbar.showSnackbar(s.message) }
                                    is ServerStatus.Connected -> onOpenDetail(server.id)
                                    else -> Unit
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { victim ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete server?") },
            text = { Text("\"${victim.name}\" (${victim.panelBaseUrl}) will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching {
                            repo.delete(victim.id)
                            tracker.clear(victim.id)
                        }.onFailure { snackbar.showSnackbar("Delete failed: ${it.message}") }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServerRow(
    server: Server,
    status: ServerStatus,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onCopyUrl: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onStatusClick: () -> Unit,
) {
    ContextMenuWrapper(
        items = listOf(
            ContextMenuItem("Change", onEdit),
            ContextMenuItem("Copy URL", onCopyUrl),
            ContextMenuItem("Clone", onClone),
            ContextMenuItem("Delete", onDelete),
        ),
        onPrimaryClick = onOpenDetail,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(server.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    server.panelBaseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (server.inbounds.isNotEmpty()) {
                    Text(
                        "${server.inbounds.size} inbound(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(status = status, onClick = onStatusClick)
        }
    }
}

@Composable
private fun StatusChip(status: ServerStatus, onClick: () -> Unit) {
    val (color, label) = when (status) {
        ServerStatus.Unknown -> MaterialTheme.colorScheme.outline to "—"
        ServerStatus.Checking -> MaterialTheme.colorScheme.primary to "Checking…"
        is ServerStatus.Connected -> {
            val s = status.snapshot
            val xrayMark = if (s.xrayState.equals("running", ignoreCase = true)) "✓" else "✗"
            Color(0xFF2E7D32) to
                "$xrayMark xray ${s.xrayVersion.ifBlank { "?" }} · " +
                "CPU ${s.cpuPercent.roundToInt()}% · " +
                "MEM ${s.memPercent.roundToInt()}%"
        }
        is ServerStatus.Failed -> MaterialTheme.colorScheme.error to truncate(status.message, 50)
    }
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status is ServerStatus.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
        }
        TextButton(onClick = onClick) { Text(label, color = color) }
    }
}

private fun truncate(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun ErrorBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Failed to load servers: $message", color = MaterialTheme.colorScheme.error)
    }
}
