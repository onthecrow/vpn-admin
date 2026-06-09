package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.onthecrow.vpnadmin.data.VpnConfig
import com.onthecrow.vpnadmin.ui.ContextMenuItem
import com.onthecrow.vpnadmin.ui.ContextMenuWrapper
import com.onthecrow.vpnadmin.ui.EditSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigDetailScreen(
    configId: String,
    repo: ConfigRepository,
    session: EditSession,
    onBack: () -> Unit,
    onEditVpn: (configId: String, vpnId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VpnConfig?>(null) }
    var discardPrompt by remember { mutableStateOf(false) }
    val original = remember { mutableStateOf<com.onthecrow.vpnadmin.data.TopLevelConfig?>(null) }

    LaunchedEffect(configId) {
        if (session.draft?.id != configId) {
            runCatching { repo.get(configId) }
                .onSuccess { fetched ->
                    if (fetched == null) {
                        loadError = "Config not found"
                    } else {
                        session.load(fetched)
                        original.value = fetched
                    }
                }
                .onFailure { loadError = it.message }
        } else if (original.value == null) {
            original.value = session.draft
        }
    }

    val draft = session.draft
    val dirty = draft != null && draft != original.value

    val attemptBack: () -> Unit = {
        if (dirty) discardPrompt = true else { session.clear(); onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(draft?.name?.ifBlank { "(unnamed)" } ?: "Loading…") },
                navigationIcon = {
                    TextButton(onClick = attemptBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        enabled = dirty && !saving,
                        onClick = {
                            val current = draft ?: return@TextButton
                            saving = true
                            scope.launch {
                                runCatching { repo.update(current) }
                                    .onSuccess {
                                        original.value = current
                                        snackbar.showSnackbar("Saved")
                                    }
                                    .onFailure {
                                        snackbar.showSnackbar("Save failed: ${it.message}")
                                    }
                                saving = false
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (draft != null) {
                FloatingActionButton(onClick = { onEditVpn(configId, null) }) {
                    Text("+")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $loadError", color = MaterialTheme.colorScheme.error)
                }
                draft == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { newName ->
                            session.update { it.copy(name = newName) }
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    Text(
                        "VPN configs (${draft.configs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HorizontalDivider()
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(draft.configs, key = { it.id.ifBlank { it.hashCode().toString() } }) { vpn ->
                            VpnConfigRow(
                                vpn = vpn,
                                onOpen = { onEditVpn(configId, vpn.id) },
                                onCopyId = {
                                    clipboard.setText(AnnotatedString(vpn.id))
                                    scope.launch { snackbar.showSnackbar("Copied: ${vpn.id}") }
                                },
                                onClone = {
                                    session.update { current ->
                                        val cloned = vpn.copy(id = vpn.id + "-copy")
                                        current.copy(configs = current.configs + cloned)
                                    }
                                },
                                onDelete = { pendingDelete = vpn },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { victim ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete VPN entry?") },
            text = { Text("\"${victim.name}\" (${victim.id}) will be removed from this config.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    session.update { it.copy(configs = it.configs.filterNot { v -> v === victim }) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (discardPrompt) {
        AlertDialog(
            onDismissRequest = { discardPrompt = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Go back without saving?") },
            confirmButton = {
                TextButton(onClick = {
                    discardPrompt = false
                    session.clear()
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { discardPrompt = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun VpnConfigRow(
    vpn: VpnConfig,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(vpn.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
            val typeLabel = when (vpn.type) {
                com.onthecrow.vpnadmin.data.TemplateConfigType.DIRECT -> "Direct"
                com.onthecrow.vpnadmin.data.TemplateConfigType.CASCADE -> "Cascade"
            }
            Text(
                "${vpn.id}  ·  ${vpn.location}  ·  $typeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
