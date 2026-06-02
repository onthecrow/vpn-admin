package com.onthecrow.vpnadmin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.onthecrow.vpnadmin.data.ConfigRepository
import com.onthecrow.vpnadmin.data.FirestoreConfigRepository
import com.onthecrow.vpnadmin.data.FirestoreServerRepository
import com.onthecrow.vpnadmin.data.FirestoreTemplateRepository
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.data.TemplateRepository
import com.onthecrow.vpnadmin.firebase.FirebaseHandles
import com.onthecrow.vpnadmin.firebase.FirebaseInitializer
import com.onthecrow.vpnadmin.nav.ConfigDetailRoute
import com.onthecrow.vpnadmin.nav.ConfigsListRoute
import com.onthecrow.vpnadmin.nav.RootTab
import com.onthecrow.vpnadmin.nav.Route
import com.onthecrow.vpnadmin.nav.ServerDetailRoute
import com.onthecrow.vpnadmin.nav.ServerEditRoute
import com.onthecrow.vpnadmin.nav.ServersListRoute
import com.onthecrow.vpnadmin.nav.TemplateRoute
import com.onthecrow.vpnadmin.nav.VpnConfigEditRoute
import com.onthecrow.vpnadmin.threexui.ServerStatusTracker
import com.onthecrow.vpnadmin.threexui.ThreeXUiClient
import com.onthecrow.vpnadmin.ui.EditSession
import com.onthecrow.vpnadmin.ui.RootShell
import com.onthecrow.vpnadmin.ui.screens.ConfigDetailScreen
import com.onthecrow.vpnadmin.ui.screens.ConfigsListScreen
import com.onthecrow.vpnadmin.ui.screens.ServerDetailScreen
import com.onthecrow.vpnadmin.ui.screens.ServerEditScreen
import com.onthecrow.vpnadmin.ui.screens.ServersListScreen
import com.onthecrow.vpnadmin.ui.screens.TemplateScreen
import com.onthecrow.vpnadmin.ui.screens.VpnConfigEditScreen

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf<BootState>(BootState.Loading) }

            LaunchedEffect(Unit) {
                state = FirebaseInitializer.initAndSignIn()
                    .fold(
                        onSuccess = { BootState.Ready(it) },
                        onFailure = { BootState.Failed(it.message ?: it::class.simpleName.orEmpty()) },
                    )
            }

            when (val s = state) {
                BootState.Loading -> BootSplash("Connecting to Firebase…")
                is BootState.Failed -> BootSplash("Failed: ${s.message}", error = true)
                is BootState.Ready -> AppContent(handles = s.handles)
            }
        }
    }
}

@Composable
private fun AppContent(handles: FirebaseHandles) {
    val scope = rememberCoroutineScope()
    val configRepo: ConfigRepository = remember(handles) { FirestoreConfigRepository(handles.firestore) }
    val serverRepo: ServerRepository = remember(handles) { FirestoreServerRepository(handles.firestore) }
    val templateRepo: TemplateRepository = remember(handles) { FirestoreTemplateRepository(handles.firestore) }

    val tracker = remember(serverRepo) {
        ServerStatusTracker(ThreeXUiClient(), serverRepo, scope)
    }

    var tab by remember { mutableStateOf(RootTab.Configs) }
    val configsBackStack = remember { mutableStateListOf<Route>(ConfigsListRoute) }
    val serversBackStack = remember { mutableStateListOf<Route>(ServersListRoute) }
    val templateBackStack = remember { mutableStateListOf<Route>(TemplateRoute) }
    val session = remember { EditSession() }

    fun popConfigs() { if (configsBackStack.size > 1) configsBackStack.removeAt(configsBackStack.lastIndex) }
    fun popServers() { if (serversBackStack.size > 1) serversBackStack.removeAt(serversBackStack.lastIndex) }
    fun popTemplate() { if (templateBackStack.size > 1) templateBackStack.removeAt(templateBackStack.lastIndex) }

    RootShell(selected = tab, onSelect = { tab = it }) {
        when (tab) {
            RootTab.Configs -> NavDisplay(
                backStack = configsBackStack,
                onBack = { popConfigs() },
                entryProvider = { key ->
                    when (key) {
                        is ConfigsListRoute -> NavEntry(key) {
                            ConfigsListScreen(
                                repo = configRepo,
                                onOpenConfig = { id -> configsBackStack.add(ConfigDetailRoute(id)) },
                            )
                        }
                        is ConfigDetailRoute -> NavEntry(key) {
                            ConfigDetailScreen(
                                configId = key.id,
                                repo = configRepo,
                                session = session,
                                onBack = { popConfigs() },
                                onEditVpn = { parentId, vpnId ->
                                    configsBackStack.add(VpnConfigEditRoute(parentId, vpnId))
                                },
                            )
                        }
                        is VpnConfigEditRoute -> NavEntry(key) {
                            VpnConfigEditScreen(
                                parentConfigId = key.parentConfigId,
                                vpnConfigId = key.vpnConfigId,
                                session = session,
                                onDone = { popConfigs() },
                            )
                        }
                        else -> NavEntry(key) { Text("Unknown route: $key") }
                    }
                },
            )
            RootTab.Servers -> NavDisplay(
                backStack = serversBackStack,
                onBack = { popServers() },
                entryProvider = { key ->
                    when (key) {
                        is ServersListRoute -> NavEntry(key) {
                            ServersListScreen(
                                repo = serverRepo,
                                tracker = tracker,
                                onOpenDetail = { id -> serversBackStack.add(ServerDetailRoute(id)) },
                                onEditServer = { id -> serversBackStack.add(ServerEditRoute(id)) },
                            )
                        }
                        is ServerDetailRoute -> NavEntry(key) {
                            ServerDetailScreen(
                                serverId = key.id,
                                repo = serverRepo,
                                tracker = tracker,
                                onBack = { popServers() },
                            )
                        }
                        is ServerEditRoute -> NavEntry(key) {
                            ServerEditScreen(
                                serverId = key.id,
                                repo = serverRepo,
                                tracker = tracker,
                                onDone = { popServers() },
                            )
                        }
                        else -> NavEntry(key) { Text("Unknown route: $key") }
                    }
                },
            )
            RootTab.Template -> NavDisplay(
                backStack = templateBackStack,
                onBack = { popTemplate() },
                entryProvider = { key ->
                    when (key) {
                        is TemplateRoute -> NavEntry(key) {
                            TemplateScreen(
                                repo = templateRepo,
                                serverRepo = serverRepo,
                            )
                        }
                        else -> NavEntry(key) { Text("Unknown route: $key") }
                    }
                },
            )
        }
    }
}

@Composable
private fun BootSplash(message: String, error: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (error) {
            Text(message, color = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator()
        }
    }
}

private sealed interface BootState {
    data object Loading : BootState
    data class Ready(val handles: FirebaseHandles) : BootState
    data class Failed(val message: String) : BootState
}
