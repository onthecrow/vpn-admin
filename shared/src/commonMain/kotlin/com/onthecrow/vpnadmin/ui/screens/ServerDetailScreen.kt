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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.InboundSummary
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.threexui.ServerSnapshot
import com.onthecrow.vpnadmin.threexui.ServerStatus
import com.onthecrow.vpnadmin.threexui.ServerStatusTracker
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: String,
    repo: ServerRepository,
    tracker: ServerStatusTracker,
    onBack: () -> Unit,
) {
    // Observe the server document live — when tracker writes new inbounds, list refreshes.
    val server: Server? by remember(serverId) {
        repo.observeAll().map { list -> list.firstOrNull { it.id == serverId } }
    }.collectAsState(initial = null)

    val status: ServerStatus = tracker.status(serverId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(server?.name?.ifBlank { "(unnamed)" } ?: "Server") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    val s = server
                    TextButton(
                        enabled = s != null && status !is ServerStatus.Checking,
                        onClick = { s?.let(tracker::check) },
                    ) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = server) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Column(Modifier.fillMaxSize()) {
                    StatusSection(status)
                    HorizontalDivider()
                    InboundsSection(s.inbounds)
                }
            }
        }
    }
}

@Composable
private fun StatusSection(status: ServerStatus) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionHeader("Status")
        when (status) {
            ServerStatus.Unknown -> Text("Tap Refresh to fetch status.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ServerStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Checking…")
            }
            is ServerStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape),
                )
                Text(status.message, color = MaterialTheme.colorScheme.error)
            }
            is ServerStatus.Connected -> SnapshotRows(status.snapshot)
        }
    }
}

@Composable
private fun SnapshotRows(snap: ServerSnapshot) {
    InfoRow("xray", "${snap.xrayState} · ${snap.xrayVersion.ifBlank { "?" }}")
    InfoRow("CPU", "${pct(snap.cpuPercent)} · load ${snap.load1} / ${snap.load5} / ${snap.load15}")
    InfoRow("Memory", "${formatBytes(snap.memUsedBytes)} / ${formatBytes(snap.memTotalBytes)} (${pct(snap.memPercent)})")
    InfoRow("Swap", "${formatBytes(snap.swapUsedBytes)} / ${formatBytes(snap.swapTotalBytes)}")
    InfoRow("Disk", "${formatBytes(snap.diskUsedBytes)} / ${formatBytes(snap.diskTotalBytes)} (${pct(snap.diskPercent)})")
    InfoRow("Net", "↑ ${formatBytes(snap.netUpBytes)} · ↓ ${formatBytes(snap.netDownBytes)}")
    InfoRow("TCP conns", snap.tcpCount.toString())
}

@Composable
private fun InboundsSection(inbounds: List<InboundSummary>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Inbounds (${inbounds.size})")
        }
        HorizontalDivider()
        if (inbounds.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No inbounds synced yet. Tap Refresh to fetch.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(inbounds, key = { it.id }) { inb ->
                    InboundRow(inb)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InboundRow(inb: InboundSummary) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${inb.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${inb.protocol}:${inb.port}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            inb.remark.ifBlank { "(no remark)" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.padding(end = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value)
    }
}

private fun pct(value: Double): String = "${(value * 10).roundToInt() / 10.0}%"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    val rounded = (value * 10).roundToInt() / 10.0
    return "$rounded ${units[i]}"
}
