package com.onthecrow.vpnadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onthecrow.vpnadmin.data.TemplateConfigType
import com.onthecrow.vpnadmin.data.VpnConfig
import com.onthecrow.vpnadmin.ui.EditSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnConfigEditScreen(
    parentConfigId: String,
    vpnConfigId: String?,
    session: EditSession,
    onDone: () -> Unit,
) {
    val parent = session.draft
    if (parent == null || parent.id != parentConfigId) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val existing = remember(vpnConfigId) {
        vpnConfigId?.let { id -> parent.configs.firstOrNull { it.id == id } }
    }

    var id by remember { mutableStateOf(existing?.id.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: TemplateConfigType.DIRECT) }
    var idError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        if (id.isBlank()) {
            idError = "id is required"
            return false
        }
        val collision = parent.configs.any { it.id == id && it !== existing }
        if (collision) {
            idError = "id already exists in this config"
            return false
        }
        idError = null
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vpnConfigId == null) "New VPN entry" else "Edit VPN entry") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Cancel") }
                },
                actions = {
                    TextButton(onClick = {
                        if (!validate()) return@TextButton
                        val updated = VpnConfig(
                            id = id,
                            location = location,
                            name = name,
                            url = url,
                            type = type,
                        )
                        session.update { current ->
                            val newList = if (existing == null) {
                                current.configs + updated
                            } else {
                                current.configs.map { if (it === existing) updated else it }
                            }
                            current.copy(configs = newList)
                        }
                        onDone()
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it; if (idError != null) idError = null },
                label = { Text("id") },
                singleLine = true,
                isError = idError != null,
                supportingText = idError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("url") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("type", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TemplateConfigType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, TemplateConfigType.entries.size),
                        selected = type == t,
                        onClick = { type = t },
                    ) {
                        Text(
                            when (t) {
                                TemplateConfigType.DIRECT -> "Direct"
                                TemplateConfigType.CASCADE -> "Cascade"
                            }
                        )
                    }
                }
            }
        }
    }
}
