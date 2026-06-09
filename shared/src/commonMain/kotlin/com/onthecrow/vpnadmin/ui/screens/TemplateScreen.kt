package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.InboundSummary
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.ServerRepository
import com.onthecrow.vpnadmin.data.SubscriptionTemplate
import com.onthecrow.vpnadmin.data.TemplateConfigEntry
import com.onthecrow.vpnadmin.data.TemplateConfigProtocol
import com.onthecrow.vpnadmin.data.TemplateConfigType
import com.onthecrow.vpnadmin.data.TemplateRepository
import com.onthecrow.vpnadmin.data.TemplateSlot
import com.onthecrow.vpnadmin.data.newDocId
import kotlinx.coroutines.launch

/** Identifies which config row (or new add) the dialog is targeting. */
private data class EditTarget(val slotId: String, val entry: TemplateConfigEntry?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    repo: TemplateRepository,
    serverRepo: ServerRepository,
) {
    val scope = rememberCoroutineScope()
    val template by remember { repo.observe() }.collectAsState(initial = SubscriptionTemplate())
    val servers by remember { serverRepo.observeAll() }.collectAsState(initial = emptyList())

    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var deleteSlotId by remember { mutableStateOf<String?>(null) }

    fun mutate(transform: (SubscriptionTemplate) -> SubscriptionTemplate) {
        val updated = transform(template)
        scope.launch { runCatching { repo.save(updated) } }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Template") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    mutate { t -> t.copy(slots = t.slots + TemplateSlot(id = newDocId())) }
                },
            ) { Text("+") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (template.slots.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No template slots yet. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(template.slots, key = { it.id }) { slot ->
                        SlotColumn(
                            slot = slot,
                            servers = servers,
                            onServerPicked = { newServerId ->
                                mutate { t ->
                                    t.copy(slots = t.slots.map { s ->
                                        if (s.id != slot.id) s
                                        else if (s.serverId == newServerId) s   // same server, no-op
                                        else s.copy(serverId = newServerId, configs = emptyList())
                                    })
                                }
                            },
                            onAddConfigClick = { editTarget = EditTarget(slot.id, null) },
                            onEditConfig = { entry -> editTarget = EditTarget(slot.id, entry) },
                            onDeleteConfig = { cfgId ->
                                mutate { t ->
                                    t.copy(slots = t.slots.map { s ->
                                        if (s.id == slot.id) {
                                            s.copy(configs = s.configs.filterNot { c -> c.id == cfgId })
                                        } else s
                                    })
                                }
                            },
                            onDeleteSlotClick = { deleteSlotId = slot.id },
                        )
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        val slot = template.slots.firstOrNull { it.id == target.slotId } ?: run {
            editTarget = null
            return@let
        }
        val server = servers.firstOrNull { it.id == slot.serverId }
        // Enforce "one config per inbound per slot": drop inbounds already used by *other*
        // entries in this slot (keep the inbound the entry being edited currently points to).
        val takenIds = slot.configs
            .filter { it.id != target.entry?.id }
            .mapNotNull { it.inboundId }
            .toSet()
        val availableInbounds = (server?.inbounds.orEmpty())
            .filterNot { it.id in takenIds }
        ConfigEditDialog(
            existing = target.entry,
            serverInbounds = availableInbounds,
            serverName = server?.name?.ifBlank { server.id },
            onDismiss = { editTarget = null },
            onConfirm = { built ->
                editTarget = null
                mutate { t ->
                    t.copy(slots = t.slots.map { s ->
                        if (s.id != slot.id) s
                        else {
                            val newConfigs = if (target.entry == null) {
                                s.configs + built
                            } else {
                                s.configs.map { c -> if (c.id == built.id) built else c }
                            }
                            s.copy(configs = newConfigs)
                        }
                    })
                }
            },
        )
    }

    deleteSlotId?.let { slotId ->
        val slot = template.slots.firstOrNull { it.id == slotId }
        val count = slot?.configs?.size ?: 0
        AlertDialog(
            onDismissRequest = { deleteSlotId = null },
            title = { Text("Delete column?") },
            text = { Text("This column has $count config(s). They will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteSlotId = null
                    mutate { t -> t.copy(slots = t.slots.filterNot { it.id == slotId }) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSlotId = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotColumn(
    slot: TemplateSlot,
    servers: List<Server>,
    onServerPicked: (String?) -> Unit,
    onAddConfigClick: () -> Unit,
    onEditConfig: (TemplateConfigEntry) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onDeleteSlotClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(280.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Server dropdown ──────────────────────────────────────────
            var expanded by remember { mutableStateOf(false) }
            val selected = servers.firstOrNull { it.id == slot.serverId }
            val displayText = when {
                slot.serverId == null -> "(unassigned)"
                selected == null -> "(missing server)"
                else -> selected.name.ifBlank { selected.id }
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Server") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("(unassigned)") },
                        onClick = { expanded = false; onServerPicked(null) },
                    )
                    servers.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.name.ifBlank { s.id }) },
                            onClick = { expanded = false; onServerPicked(s.id) },
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Config rows ──────────────────────────────────────────────
            if (slot.configs.isEmpty()) {
                Text(
                    "(no configs)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                slot.configs.forEach { cfg ->
                    val inbound = selected?.inbounds?.firstOrNull { it.id == cfg.inboundId }
                    ConfigEntryRow(
                        entry = cfg,
                        inbound = inbound,
                        onClick = { onEditConfig(cfg) },
                        onDelete = { onDeleteConfig(cfg.id) },
                    )
                }
            }

            val addEnabled = slot.serverId != null
            TextButton(
                enabled = addEnabled,
                onClick = onAddConfigClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (addEnabled) "+ Config" else "Select a server first")
            }

            HorizontalDivider()

            TextButton(
                onClick = onDeleteSlotClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete column", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConfigEntryRow(
    entry: TemplateConfigEntry,
    inbound: InboundSummary?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                entry.name.ifBlank { "(unnamed)" },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            val protocolLabel = inbound?.protocol?.uppercase() ?: entry.protocol.label
            val inboundLabel = when {
                inbound != null -> "#${inbound.id} ${inbound.protocol}:${inbound.port}"
                entry.inboundId != null -> "#${entry.inboundId} (missing)"
                else -> "no inbound"
            }
            Text(
                "$protocolLabel · ${entry.type.label} · $inboundLabel" +
                    if (entry.location.isNotBlank()) " · ${entry.location}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text("✕") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditDialog(
    existing: TemplateConfigEntry?,
    serverInbounds: List<InboundSummary>,
    serverName: String?,
    onDismiss: () -> Unit,
    onConfirm: (TemplateConfigEntry) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var location by remember(existing) { mutableStateOf(existing?.location.orEmpty()) }
    var type by remember(existing) { mutableStateOf(existing?.type ?: TemplateConfigType.DIRECT) }
    var inboundId by remember(existing) { mutableStateOf(existing?.inboundId) }
    var inboundExpanded by remember { mutableStateOf(false) }

    val selectedInbound = serverInbounds.firstOrNull { it.id == inboundId }
    val canSave = name.isNotBlank() && inboundId != null && selectedInbound != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add configuration" else "Edit configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Type", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TemplateConfigType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(i, TemplateConfigType.entries.size),
                            selected = type == t,
                            onClick = { type = t },
                        ) { Text(t.label) }
                    }
                }

                Text(
                    "Inbound on ${serverName ?: "server"}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (serverInbounds.isEmpty()) {
                    Text(
                        "No inbounds synced for this server yet. Open Servers → Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val display = selectedInbound?.let { "#${it.id} ${it.protocol}:${it.port} (${it.remark.ifBlank { "no remark" }})" }
                        ?: "(none)"
                    ExposedDropdownMenuBox(
                        expanded = inboundExpanded,
                        onExpandedChange = { inboundExpanded = !inboundExpanded },
                    ) {
                        OutlinedTextField(
                            value = display,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Inbound") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inboundExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = inboundExpanded,
                            onDismissRequest = { inboundExpanded = false },
                        ) {
                            serverInbounds.forEach { ib ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "#${ib.id} ${ib.protocol}:${ib.port}" +
                                                if (ib.remark.isNotBlank()) " (${ib.remark})" else ""
                                        )
                                    },
                                    onClick = {
                                        inboundExpanded = false
                                        inboundId = ib.id
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    // Derive protocol from the selected inbound — `protocol` is no longer
                    // a user choice. Fallback preserves existing value when inbound is exotic.
                    val derivedProtocol = when (selectedInbound?.protocol?.lowercase()) {
                        "vless" -> TemplateConfigProtocol.VLESS
                        "hysteria", "hysteria2" -> TemplateConfigProtocol.HYSTERIA2
                        else -> existing?.protocol ?: TemplateConfigProtocol.VLESS
                    }
                    onConfirm(
                        TemplateConfigEntry(
                            id = existing?.id ?: newDocId(),
                            name = name.trim(),
                            location = location.trim(),
                            type = type,
                            protocol = derivedProtocol,
                            inboundId = inboundId,
                        )
                    )
                },
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val TemplateConfigType.label get() = when (this) {
    TemplateConfigType.DIRECT -> "Direct"
    TemplateConfigType.CASCADE -> "Cascade"
}

private val TemplateConfigProtocol.label get() = when (this) {
    TemplateConfigProtocol.VLESS -> "VLESS"
    TemplateConfigProtocol.HYSTERIA2 -> "Hysteria2"
}
