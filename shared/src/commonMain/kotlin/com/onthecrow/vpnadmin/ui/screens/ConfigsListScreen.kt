package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.ConfigRepository
import com.onthecrow.vpnadmin.data.TopLevelConfig
import com.onthecrow.vpnadmin.data.newConfigId
import com.onthecrow.vpnadmin.ui.ContextMenuItem
import com.onthecrow.vpnadmin.ui.ContextMenuWrapper
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsListScreen(
    repo: ConfigRepository,
    onOpenConfig: (id: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var loadError by remember { mutableStateOf<String?>(null) }
    val configs by remember {
        repo.observeAll().catch { e ->
            loadError = e.message ?: e::class.simpleName
            emit(emptyList())
        }
    }.collectAsState(initial = null)

    var pendingDelete by remember { mutableStateOf<TopLevelConfig?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configs") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val id = newConfigId()
                        runCatching {
                            repo.create(TopLevelConfig(id = id, name = "Untitled"))
                        }.onSuccess { onOpenConfig(id) }
                            .onFailure { snackbar.showSnackbar("Create failed: ${it.message}") }
                    }
                },
            ) { Text("+") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loadError != null -> ErrorState(loadError!!)
                configs == null -> LoadingState()
                configs!!.isEmpty() -> EmptyState()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(configs!!, key = { it.id }) { cfg ->
                        ConfigRow(
                            cfg = cfg,
                            onOpen = { onOpenConfig(cfg.id) },
                            onCopyId = {
                                clipboard.setText(AnnotatedString(cfg.id))
                                scope.launch { snackbar.showSnackbar("Copied: ${cfg.id}") }
                            },
                            onClone = {
                                scope.launch {
                                    runCatching {
                                        repo.create(
                                            cfg.copy(
                                                id = newConfigId(),
                                                name = cfg.name + " (copy)",
                                            )
                                        )
                                    }.onFailure {
                                        snackbar.showSnackbar("Clone failed: ${it.message}")
                                    }
                                }
                            },
                            onDelete = { pendingDelete = cfg },
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
            title = { Text("Delete config?") },
            text = { Text("\"${victim.name}\" (${victim.id}) will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { repo.delete(victim.id) }
                            .onFailure { snackbar.showSnackbar("Delete failed: ${it.message}") }
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
private fun ConfigRow(
    cfg: TopLevelConfig,
    onOpen: () -> Unit,
    onCopyId: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
) {
    ContextMenuWrapper(
        items = listOf(
            ContextMenuItem("Change", onOpen),
            ContextMenuItem("Copy id", onCopyId),
            ContextMenuItem("Clone", onClone),
            ContextMenuItem("Delete", onDelete),
        ),
        onPrimaryClick = onOpen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(cfg.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
            Text(
                cfg.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${cfg.configs.size} VPN config(s)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No configs yet. Tap + to create one.")
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Failed to load configs: $message", color = MaterialTheme.colorScheme.error)
    }
}
