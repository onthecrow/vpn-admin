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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.onthecrow.vpnadmin.data.ConfigRepository
import com.onthecrow.vpnadmin.data.FirestoreConfigRepository
import com.onthecrow.vpnadmin.firebase.FirebaseHandles
import com.onthecrow.vpnadmin.firebase.FirebaseInitializer
import com.onthecrow.vpnadmin.nav.ConfigDetailRoute
import com.onthecrow.vpnadmin.nav.ConfigsListRoute
import com.onthecrow.vpnadmin.nav.Route
import com.onthecrow.vpnadmin.nav.VpnConfigEditRoute
import com.onthecrow.vpnadmin.ui.EditSession
import com.onthecrow.vpnadmin.ui.screens.ConfigDetailScreen
import com.onthecrow.vpnadmin.ui.screens.ConfigsListScreen
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
                is BootState.Ready -> AppContent(
                    repo = FirestoreConfigRepository(s.handles.firestore),
                )
            }
        }
    }
}

@Composable
private fun AppContent(repo: ConfigRepository) {
    val backStack = remember { mutableStateListOf<Route>(ConfigsListRoute) }
    val session = remember { EditSession() }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = { key ->
            when (key) {
                is ConfigsListRoute -> NavEntry(key) {
                    ConfigsListScreen(
                        repo = repo,
                        onOpenConfig = { id -> backStack.add(ConfigDetailRoute(id)) },
                    )
                }
                is ConfigDetailRoute -> NavEntry(key) {
                    ConfigDetailScreen(
                        configId = key.id,
                        repo = repo,
                        session = session,
                        onBack = { pop() },
                        onEditVpn = { parentId, vpnId ->
                            backStack.add(VpnConfigEditRoute(parentId, vpnId))
                        },
                    )
                }
                is VpnConfigEditRoute -> NavEntry(key) {
                    VpnConfigEditScreen(
                        parentConfigId = key.parentConfigId,
                        vpnConfigId = key.vpnConfigId,
                        session = session,
                        onDone = { pop() },
                    )
                }
            }
        },
    )
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
