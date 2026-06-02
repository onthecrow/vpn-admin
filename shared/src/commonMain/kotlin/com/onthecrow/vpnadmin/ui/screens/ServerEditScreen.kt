package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.data.newDocId
import com.onthecrow.vpnadmin.threexui.ServerStatusTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: String?,
    repo: ServerRepository,
    tracker: ServerStatusTracker,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var initial by remember { mutableStateOf<Server?>(null) }
    var loading by remember { mutableStateOf(serverId != null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var panelBaseUrl by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var skipTlsVerify by remember { mutableStateOf(false) }

    var connecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var discardPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        if (serverId == null) { loading = false; return@LaunchedEffect }
        runCatching { repo.get(serverId) }
            .onSuccess { s ->
                if (s == null) loadError = "Server not found"
                else {
                    initial = s
                    name = s.name
                    panelBaseUrl = s.panelBaseUrl
                    apiToken = s.apiToken
                    skipTlsVerify = s.skipTlsVerify
                }
                loading = false
            }
            .onFailure { loadError = it.message; loading = false }
    }

    val canConnect by remember {
        derivedStateOf {
            name.isNotBlank() && panelBaseUrl.isNotBlank() && apiToken.isNotBlank()
        }
    }

    val dirty by remember {
        derivedStateOf {
            val ref = initial
            if (ref == null) {
                name.isNotEmpty() || panelBaseUrl.isNotEmpty() ||
                    apiToken.isNotEmpty() || skipTlsVerify
            } else {
                name != ref.name || panelBaseUrl != ref.panelBaseUrl ||
                    apiToken != ref.apiToken || skipTlsVerify != ref.skipTlsVerify
            }
        }
    }

    val attemptBack: () -> Unit = {
        if (dirty) discardPrompt = true else onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) "New server" else "Edit server") },
                navigationIcon = { TextButton(onClick = attemptBack) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $loadError", color = MaterialTheme.colorScheme.error)
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = panelBaseUrl,
                        onValueChange = { panelBaseUrl = it },
                        label = { Text("Panel base URL") },
                        placeholder = { Text("https://host:port/web-base-path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = { apiToken = it },
                        label = { Text("API token") },
                        placeholder = { Text("3X-UI Settings → Security → API Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = skipTlsVerify,
                            onCheckedChange = { skipTlsVerify = it },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("Skip TLS verification")
                            Text(
                                "Use for self-signed certs or when connecting by IP that " +
                                    "doesn't match the cert SAN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    connectError?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            enabled = canConnect && !connecting,
                            onClick = {
                                connectError = null
                                connecting = true
                                scope.launch {
                                    val draft = Server(
                                        id = initial?.id ?: newDocId(),
                                        name = name.trim(),
                                        panelBaseUrl = panelBaseUrl.trim(),
                                        apiToken = apiToken.trim(),
                                        skipTlsVerify = skipTlsVerify,
                                        createdAt = initial?.createdAt ?: 0L,
                                        updatedAt = initial?.updatedAt ?: 0L,
                                    )
                                    val verify = runCatching {
                                        withTimeout(20_000) {
                                            tracker.client.verifyConnection(draft)
                                        }
                                    }.fold(onSuccess = { it }, onFailure = { Result.failure(it) })

                                    verify.fold(
                                        onSuccess = {
                                            val persist = runCatching {
                                                if (initial == null) repo.create(draft)
                                                else repo.update(draft)
                                            }
                                            persist.fold(
                                                onSuccess = {
                                                    tracker.check(draft)
                                                    connecting = false
                                                    onDone()
                                                },
                                                onFailure = {
                                                    connectError = "Save failed: ${it.message}"
                                                    connecting = false
                                                },
                                            )
                                        },
                                        onFailure = {
                                            connectError = it.message ?: it::class.simpleName
                                            connecting = false
                                        },
                                    )
                                }
                            },
                        ) {
                            Text(if (connecting) "Connecting…" else "Connect")
                        }
                        if (connecting) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (discardPrompt) {
        AlertDialog(
            onDismissRequest = { discardPrompt = false },
            title = { Text("Discard changes?") },
            text = { Text("Server will not be saved.") },
            confirmButton = {
                TextButton(onClick = { discardPrompt = false; onDone() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { discardPrompt = false }) { Text("Keep editing") }
            },
        )
    }
}
