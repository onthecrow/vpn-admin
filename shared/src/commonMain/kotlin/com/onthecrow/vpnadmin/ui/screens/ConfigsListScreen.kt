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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.data.SubscriptionTemplate
import com.onthecrow.vpnadmin.data.TemplateRepository
import com.onthecrow.vpnadmin.data.TopLevelConfig
import com.onthecrow.vpnadmin.data.newDocId
import com.onthecrow.vpnadmin.subscription.SubscriptionCreator
import com.onthecrow.vpnadmin.ui.ContextMenuItem
import com.onthecrow.vpnadmin.ui.ContextMenuWrapper
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private sealed interface AutoState {
    data object Idle : AutoState
    data class Invalid(val issues: List<String>) : AutoState
    data object NameInput : AutoState
    data class Progress(val stage: String) : AutoState
    data class Failed(val message: String) : AutoState
}

private sealed interface DeleteState {
    data object Idle : DeleteState
    data class Confirming(val victim: TopLevelConfig) : DeleteState
    data class Progress(val stage: String) : DeleteState
    data class Summary(val message: String) : DeleteState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsListScreen(
    repo: ConfigRepository,
    templateRepo: TemplateRepository,
    serverRepo: ServerRepository,
    creator: SubscriptionCreator,
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
    val template by remember {
        templateRepo.observe()
    }.collectAsState(initial = SubscriptionTemplate())
    val servers by remember { serverRepo.observeAll() }.collectAsState(initial = emptyList())

    var deleteState by remember { mutableStateOf<DeleteState>(DeleteState.Idle) }
    var autoState by remember { mutableStateOf<AutoState>(AutoState.Idle) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configs") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("Auto create") },
                    icon = { Text("⚙") },
                    onClick = {
                        val issues = creator.validate(template, servers)
                        autoState = if (issues.isEmpty()) AutoState.NameInput
                        else AutoState.Invalid(issues)
                    },
                )
                ExtendedFloatingActionButton(
                    text = { Text("Create") },
                    icon = { Text("+") },
                    onClick = {
                        scope.launch {
                            val id = newDocId()
                            runCatching {
                                repo.create(TopLevelConfig(id = id, name = "Untitled"))
                            }.onSuccess { onOpenConfig(id) }
                                .onFailure { snackbar.showSnackbar("Create failed: ${it.message}") }
                        }
                    },
                )
            }
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
                                                id = newDocId(),
                                                name = cfg.name + " (copy)",
                                            )
                                        )
                                    }.onFailure {
                                        snackbar.showSnackbar("Clone failed: ${it.message}")
                                    }
                                }
                            },
                            onDelete = { deleteState = DeleteState.Confirming(cfg) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    when (val ds = deleteState) {
        DeleteState.Idle -> Unit
        is DeleteState.Confirming -> AlertDialog(
            onDismissRequest = { deleteState = DeleteState.Idle },
            title = { Text("Delete subscription?") },
            text = {
                Text(
                    "\"${ds.victim.name}\" (${ds.victim.id}) will be removed from Firestore and " +
                        "all matching clients (subId = ${ds.victim.id}) will be deleted from " +
                        "every server."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val victim = ds.victim
                    deleteState = DeleteState.Progress("Starting…")
                    scope.launch {
                        val summary = creator.autoDelete(
                            subscription = victim,
                            servers = servers,
                            onProgress = { stage -> deleteState = DeleteState.Progress(stage) },
                        )
                        deleteState = if (summary.fullySuccessful) {
                            DeleteState.Idle
                        } else {
                            val header = buildString {
                                append("Removed ${summary.totalDeletedClients} client(s) from ")
                                append("${summary.serversTouched} server(s). ")
                                append(
                                    if (summary.firestoreDeleted)
                                        "Firestore record removed."
                                    else
                                        "Firestore record NOT removed — try again."
                                )
                            }
                            val issues = summary.errors.joinToString("\n") { "• $it" }
                            DeleteState.Summary("$header\n\nIssues:\n$issues")
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteState = DeleteState.Idle }) { Text("Cancel") }
            },
        )
        is DeleteState.Progress -> ProgressDialog(stage = ds.stage)
        is DeleteState.Summary -> AlertDialog(
            onDismissRequest = { deleteState = DeleteState.Idle },
            title = { Text("Delete finished with issues") },
            text = { Text(ds.message) },
            confirmButton = { TextButton(onClick = { deleteState = DeleteState.Idle }) { Text("OK") } },
        )
    }

    when (val s = autoState) {
        AutoState.Idle -> Unit
        is AutoState.Invalid -> InvalidTemplateDialog(
            issues = s.issues,
            onDismiss = { autoState = AutoState.Idle },
        )
        AutoState.NameInput -> NameInputDialog(
            onCancel = { autoState = AutoState.Idle },
            onCreate = { typedName ->
                autoState = AutoState.Progress("Starting…")
                scope.launch {
                    val result = creator.autoCreate(
                        name = typedName,
                        template = template,
                        servers = servers,
                        onProgress = { stage -> autoState = AutoState.Progress(stage) },
                    )
                    result.fold(
                        onSuccess = { newId ->
                            clipboard.setText(AnnotatedString(newId))
                            autoState = AutoState.Idle
                            onOpenConfig(newId)
                        },
                        onFailure = { err ->
                            autoState = AutoState.Failed(err.message ?: err::class.simpleName.orEmpty())
                        },
                    )
                }
            },
        )
        is AutoState.Progress -> ProgressDialog(stage = s.stage)
        is AutoState.Failed -> AlertDialog(
            onDismissRequest = { autoState = AutoState.Idle },
            title = { Text("Auto-create failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { autoState = AutoState.Idle }) { Text("OK") } },
        )
    }
}

@Composable
private fun NameInputDialog(
    onCancel: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Auto-create subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Generates a fresh subscription id, then creates one client per " +
                        "server (with all template inbounds) and pulls the vpn links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subscription name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ProgressDialog(stage: String) {
    AlertDialog(
        onDismissRequest = {}, // not cancellable
        title = { Text("Creating subscription…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stage)
            }
        },
        confirmButton = {}, // hide button
    )
}

@Composable
private fun InvalidTemplateDialog(issues: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Template is not ready") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fix the following on the Template tab:")
                issues.forEach { Text("• $it") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
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
