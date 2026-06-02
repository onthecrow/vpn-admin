package com.onthecrow.vpnadmin.threexui

import androidx.compose.runtime.mutableStateMapOf
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface ServerStatus {
    data object Unknown : ServerStatus
    data object Checking : ServerStatus
    data class Connected(val checkedAt: Long, val snapshot: ServerSnapshot) : ServerStatus
    data class Failed(val message: String) : ServerStatus
}

@OptIn(ExperimentalTime::class)
class ServerStatusTracker(
    val client: ThreeXUiClient,
    private val repo: ServerRepository,
    private val scope: CoroutineScope,
) {
    private val states = mutableStateMapOf<String, ServerStatus>()
    private val jobs = mutableMapOf<String, Job>()

    fun status(id: String): ServerStatus = states[id] ?: ServerStatus.Unknown

    fun check(server: Server) {
        jobs[server.id]?.cancel()
        states[server.id] = ServerStatus.Checking
        jobs[server.id] = scope.launch {
            val snapDeferred = async { client.fetchStatus(server) }
            val ibsDeferred = async { client.listInbounds(server) }
            val snap = snapDeferred.await()
            val ibs = ibsDeferred.await()

            // Status reflects the snapshot endpoint — inbounds is best-effort sync.
            states[server.id] = snap.fold(
                onSuccess = { s ->
                    ServerStatus.Connected(
                        checkedAt = Clock.System.now().toEpochMilliseconds(),
                        snapshot = s,
                    )
                },
                onFailure = { ServerStatus.Failed(it.message ?: it::class.simpleName.orEmpty()) },
            )

            // Persist inbounds when both succeed AND list actually changed (avoid no-op writes).
            ibs.getOrNull()?.let { fresh ->
                if (fresh != server.inbounds) {
                    runCatching { repo.update(server.copy(inbounds = fresh)) }
                }
            }
        }
    }

    fun checkAll(servers: List<Server>) {
        servers.forEach(::check)
    }

    fun clear(id: String) {
        jobs.remove(id)?.cancel()
        states.remove(id)
    }
}
