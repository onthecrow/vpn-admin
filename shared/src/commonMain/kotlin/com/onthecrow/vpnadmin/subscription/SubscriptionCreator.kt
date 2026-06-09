package com.onthecrow.vpnadmin.subscription

import com.onthecrow.vpnadmin.data.ConfigRepository
import com.onthecrow.vpnadmin.data.InboundSummary
import com.onthecrow.vpnadmin.data.Server
import com.onthecrow.vpnadmin.data.SubscriptionTemplate
import com.onthecrow.vpnadmin.data.TemplateConfigEntry
import com.onthecrow.vpnadmin.data.TopLevelConfig
import com.onthecrow.vpnadmin.data.VpnConfig
import com.onthecrow.vpnadmin.data.newDocId
import com.onthecrow.vpnadmin.data.newSubscriptionId
import com.onthecrow.vpnadmin.threexui.ThreeXUiClient

/**
 * Orchestrates auto-creation of a subscription from the saved template:
 *   1. Validate template + servers
 *   2. Generate a fresh subId
 *   3. Group template configs by server (multiple slots/configs collapse to one client
 *      attached to a list of inbounds per server)
 *   4. Per server: list existing clients → compute unique email → addClient → fetch links
 *   5. Match each returned link to its originating template entry by inbound port
 *   6. Persist one TopLevelConfig (subscription) to Firestore
 *
 * Fail-fast: the first error stops the flow. Successful servers BEFORE the failure are
 * left as-is (the resulting clients exist on the panel without a Firestore record). Caller
 * is responsible for showing a clear error so the operator can manually clean up.
 */
class SubscriptionCreator(
    private val client: ThreeXUiClient,
    private val configRepo: ConfigRepository,
) {

    /** Returns a list of human-readable issues. Empty list = template is ready for auto-create. */
    fun validate(template: SubscriptionTemplate, servers: List<Server>): List<String> {
        val issues = mutableListOf<String>()
        if (template.slots.isEmpty()) {
            issues += "Template has no columns. Open Template and add at least one."
            return issues
        }
        var hasAnyConfig = false
        template.slots.forEachIndexed { i, slot ->
            val slotLabel = "Column #${i + 1}"
            val server = servers.firstOrNull { it.id == slot.serverId }
            when {
                slot.serverId == null -> issues += "$slotLabel: no server selected."
                server == null -> issues +=
                    "$slotLabel: server '${slot.serverId}' not found — was it deleted?"
            }
            if (slot.configs.isEmpty()) {
                issues += "$slotLabel: no configurations."
            } else {
                hasAnyConfig = true
                slot.configs.forEachIndexed { j, entry ->
                    val label = "$slotLabel · config #${j + 1}"
                    if (entry.name.isBlank()) issues += "$label: name is empty."
                    if (entry.inboundId == null) {
                        issues += "$label: no inbound selected."
                    } else if (server != null && server.inbounds.none { it.id == entry.inboundId }) {
                        issues +=
                            "$label: inbound #${entry.inboundId} not present on server. " +
                                "Refresh the server."
                    }
                }
            }
        }
        if (!hasAnyConfig) issues += "Template must contain at least one configuration."
        return issues
    }

    /**
     * Returns the id of the newly created `configs/{id}` document. Throws on first failure
     * with a detailed message — caller wraps it in UI.
     *
     * [onProgress] receives short status strings for the progress dialog.
     */
    suspend fun autoCreate(
        name: String,
        template: SubscriptionTemplate,
        servers: List<Server>,
        onProgress: (String) -> Unit,
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Subscription name is empty." }
        val issues = validate(template, servers)
        if (issues.isNotEmpty()) {
            error("Template is invalid:\n" + issues.joinToString("\n") { "• $it" })
        }

        val subId = newSubscriptionId()
        onProgress("Generated subscription id $subId")

        // Group entries by server (across slots). Multiple slots pointing at the same server
        // collapse into a single client with a merged list of inbound ids.
        data class ServerWork(
            val server: Server,
            val entries: List<TemplateConfigEntry>,
            val inbounds: List<InboundSummary>,
        )

        val work: List<ServerWork> = template.slots
            .asSequence()
            .filter { it.serverId != null && it.configs.isNotEmpty() }
            .flatMap { slot -> slot.configs.asSequence().map { slot.serverId!! to it } }
            .groupBy({ it.first }, { it.second })
            .map { (serverId, entries) ->
                val server = servers.first { it.id == serverId }
                val inboundIds = entries.mapNotNull { it.inboundId }.distinct()
                val inbounds = server.inbounds.filter { it.id in inboundIds }
                if (inbounds.size != inboundIds.size) {
                    val missing = inboundIds - inbounds.map { it.id }.toSet()
                    error(
                        "Server '${server.name}': inbound(s) ${missing.joinToString()} not found. " +
                            "Refresh the server."
                    )
                }
                ServerWork(server, entries, inbounds)
            }

        val producedConfigs = mutableListOf<VpnConfig>()
        for (w in work) {
            val serverLabel = w.server.name.ifBlank { w.server.id }

            onProgress("Listing existing clients on $serverLabel…")
            val existing = client.listClients(w.server).getOrElse {
                error("Failed to list clients on $serverLabel: ${it.message ?: it::class.simpleName}")
            }
            val takenEmails = existing.map { it.email }.toSet()
            val email = uniqueEmail(name, takenEmails)

            onProgress("Creating client '$email' on $serverLabel…")
            client.addClient(w.server, w.inbounds, email, subId).getOrElse {
                error("Failed to create client on $serverLabel: ${it.message ?: it::class.simpleName}")
            }

            onProgress("Fetching links for '$email' on $serverLabel…")
            val links = client.getClientLinks(w.server, email).getOrElse {
                error("Client created on $serverLabel but failed to fetch links: ${it.message ?: it::class.simpleName}")
            }

            // Match each template entry → one link by inbound port. Unmatched entries get an
            // empty url (better than dropping them silently — visible in the UI).
            w.entries.forEach { entry ->
                val inbound = w.inbounds.first { it.id == entry.inboundId }
                val url = links.firstOrNull { extractPort(it) == inbound.port }.orEmpty()
                producedConfigs += VpnConfig(
                    id = newDocId(),
                    name = entry.name,
                    location = entry.location,
                    url = url,
                    type = entry.type,
                )
            }
        }

        onProgress("Saving subscription…")
        val configDocId = subId
        configRepo.create(
            TopLevelConfig(
                id = configDocId,
                name = name.trim(),
                configs = producedConfigs,
            )
        )
        configDocId
    }

    /**
     * Remove client(s) belonging to [subscription] from every server in [servers] (matched
     * by subscription id == client.subId), then delete the Firestore document.
     *
     * Best-effort: per-server errors are collected, **the Firestore record is removed at the
     * end regardless** so the UI doesn't end up with a ghost. If any server fails, caller
     * shows the summary so the operator can clean up manually.
     */
    suspend fun autoDelete(
        subscription: TopLevelConfig,
        servers: List<Server>,
        onProgress: (String) -> Unit,
    ): DeleteSummary {
        val subId = subscription.id
        val errors = mutableListOf<String>()
        var totalDeleted = 0
        var serversTouched = 0

        for (server in servers) {
            val label = server.name.ifBlank { server.id }
            onProgress("Listing clients on $label…")
            val list = client.listClients(server).getOrElse { err ->
                errors += "$label · list failed: ${err.message ?: err::class.simpleName}"
                null
            } ?: continue

            val emails = list.filter { it.subId == subId }.map { it.email }
            if (emails.isEmpty()) continue
            serversTouched++

            onProgress("Removing ${emails.size} client(s) from $label…")
            client.bulkDeleteClients(server, emails).fold(
                onSuccess = { totalDeleted += emails.size },
                onFailure = { err ->
                    errors += "$label · delete failed: ${err.message ?: err::class.simpleName}"
                },
            )
        }

        onProgress("Removing subscription record…")
        val firestoreError = runCatching { configRepo.delete(subId) }
            .exceptionOrNull()
            ?.let { "Firestore: ${it.message ?: it::class.simpleName}" }
        if (firestoreError != null) errors += firestoreError

        return DeleteSummary(
            totalDeletedClients = totalDeleted,
            serversTouched = serversTouched,
            firestoreDeleted = firestoreError == null,
            errors = errors,
        )
    }

    private fun uniqueEmail(base: String, taken: Set<String>): String {
        val trimmed = base.trim()
        if (trimmed !in taken) return trimmed
        var n = 2
        while ("${trimmed}_$n" in taken) n++
        return "${trimmed}_$n"
    }

    /** `vless://uuid@host:443?...` → 443. Works for any `scheme://...:port[?/#]` URL. */
    private fun extractPort(link: String): Long? {
        val regex = Regex(":(\\d+)(?:[?/#]|$)")
        return regex.find(link)?.groupValues?.get(1)?.toLongOrNull()
    }
}

data class DeleteSummary(
    /** How many clients across all servers were actually removed. */
    val totalDeletedClients: Int,
    /** Number of servers that had at least one matching client (we issued a bulkDel call). */
    val serversTouched: Int,
    /** True when the Firestore record was removed. */
    val firestoreDeleted: Boolean,
    /** Per-server / per-step error messages — empty list = full success. */
    val errors: List<String>,
) {
    val fullySuccessful: Boolean get() = errors.isEmpty()
}
