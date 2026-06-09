package com.onthecrow.vpnadmin.threexui

import com.onthecrow.vpnadmin.data.InboundSummary
import com.onthecrow.vpnadmin.data.Server
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ThreeXUiClient {

    /** Snapshot of system + xray state. Maps `GET /panel/api/server/status`. */
    suspend fun fetchStatus(server: Server): Result<ServerSnapshot> = runCatching {
        val obj = callApi(server, "/panel/api/server/status").jsonObject
        parseSnapshot(obj)
    }

    /** Lightweight per-inbound projection. Maps `GET /panel/api/inbounds/options`. */
    suspend fun listInbounds(server: Server): Result<List<InboundSummary>> = runCatching {
        val arr = callApi(server, "/panel/api/inbounds/options").jsonArray
        arr.map { el ->
            val o = el.jsonObject
            InboundSummary(
                id = o["id"]?.asLong() ?: 0L,
                remark = o["remark"]?.asString().orEmpty(),
                protocol = o["protocol"]?.asString().orEmpty(),
                port = o["port"]?.asLong() ?: 0L,
            )
        }
    }

    /**
     * Create a client attached to one or more [inbounds] of [server]. The panel makes one
     * client row with one email + subId, but it shows up under every inbound in the list
     * and yields one link per inbound (via [getClientLinks]).
     *
     * `flow: xtls-rprx-vision` is included iff **any** inbound in the list has VLESS
     * protocol — hysteria / hysteria2 inbounds ignore it server-side. `enable` is hardcoded
     * `true`. Other client fields (`totalGB`, `expiryTime`, `limitIp`) get panel defaults.
     *
     * Maps `POST /panel/api/clients/add`.
     */
    suspend fun addClient(
        server: Server,
        inbounds: List<InboundSummary>,
        email: String,
        subId: String,
    ): Result<Unit> = runCatching {
        if (email.isBlank()) error("email is required")
        if (subId.isBlank()) error("subId is required")
        if (inbounds.isEmpty()) error("at least one inbound is required")

        val anyVless = inbounds.any { it.protocol.equals("vless", ignoreCase = true) }
        val clientObj = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("subId", JsonPrimitive(subId))
            put("enable", JsonPrimitive(true))
            if (anyVless) put("flow", JsonPrimitive("xtls-rprx-vision"))
        }
        val body = buildJsonObject {
            put("client", clientObj)
            put("inboundIds", buildJsonArray { inbounds.forEach { add(JsonPrimitive(it.id)) } })
        }
        callApi(server, "/panel/api/clients/add", method = HttpMethod.Post, jsonBody = body)
    }

    /** Lightweight list of clients (`email` + `subId`) — used to dedupe email on creation. */
    suspend fun listClients(server: Server): Result<List<ClientSummary>> = runCatching {
        val arr = callApi(server, "/panel/api/clients/list").jsonArray
        arr.map { el ->
            val o = el.jsonObject
            ClientSummary(
                email = o["email"]?.asString().orEmpty(),
                subId = o["subId"]?.asString().orEmpty(),
            )
        }
    }

    /** Subscription links for a client by email — one URL per attached inbound. */
    suspend fun getClientLinks(server: Server, email: String): Result<List<String>> = runCatching {
        val arr = callApi(server, "/panel/api/clients/links/$email").jsonArray
        arr.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    }

    /**
     * Delete clients by email in one panel-side transaction. `keepTraffic = false` drops
     * the traffic rows too. Maps `POST /panel/api/clients/bulkDel`.
     */
    suspend fun bulkDeleteClients(server: Server, emails: List<String>): Result<Unit> = runCatching {
        if (emails.isEmpty()) return@runCatching
        val body = buildJsonObject {
            put("emails", buildJsonArray { emails.forEach { add(JsonPrimitive(it)) } })
            put("keepTraffic", JsonPrimitive(false))
        }
        callApi(server, "/panel/api/clients/bulkDel", method = HttpMethod.Post, jsonBody = body)
    }

    /** Back-compat for ServerEditScreen's "Connect" button — only needs success/failure. */
    suspend fun verifyConnection(server: Server): Result<ServerSnapshot> = fetchStatus(server)

    // ───────────────────── shared HTTP plumbing ─────────────────────

    private suspend fun callApi(
        server: Server,
        path: String,
        method: HttpMethod = HttpMethod.Get,
        jsonBody: JsonElement? = null,
    ): JsonElement {
        validate(server)
        val base = server.normalizedBase
        val http: HttpClient = createPanelHttpClient(skipTlsVerify = server.skipTlsVerify)
        try {
            val resp = try {
                http.request("$base$path") {
                    this.method = method
                    bearerAuth(server.apiToken)
                    headers.append(HttpHeaders.Accept, "application/json")
                    if (jsonBody != null) {
                        contentType(ContentType.Application.Json)
                        setBody(jsonBody.toString())
                    }
                }
            } catch (e: Throwable) {
                error("$path failed: ${e::class.simpleName} — ${e.message}")
            }
            val status = "${resp.status.value} ${resp.status.description}"
            if (!resp.status.isSuccess()) {
                val preview = runCatching { resp.bodyAsText() }.getOrDefault("").take(160)
                error(
                    if (preview.isEmpty()) "HTTP $status (empty body) at $path"
                    else "HTTP $status at $path. Body: \"${preview.replace('\n', ' ')}\""
                )
            }
            val envelope = try {
                resp.body<ApiEnvelope>()
            } catch (e: Throwable) {
                val text = runCatching { resp.bodyAsText() }.getOrDefault("")
                error(
                    "HTTP $status at $path but body isn't JSON (${e::class.simpleName}). " +
                        "Preview: \"${text.take(160).replace('\n', ' ')}\""
                )
            }
            if (!envelope.success) {
                error(envelope.msg ?: "API responded success=false at $path")
            }
            return envelope.obj ?: JsonObject(emptyMap())
        } finally {
            http.close()
        }
    }

    private fun validate(server: Server) {
        if (server.panelBaseUrl.isBlank()) error("Panel base URL is empty")
        if (server.apiToken.isBlank()) {
            error("API token is required (Settings → Security → API Token in 3X-UI)")
        }
    }

    private fun parseSnapshot(obj: JsonObject): ServerSnapshot {
        val mem = obj["mem"]?.jsonObject
        val swap = obj["swap"]?.jsonObject
        val disk = obj["disk"]?.jsonObject
        val netIO = obj["netIO"]?.jsonObject
        val xray = obj["xray"]?.jsonObject
        val load = obj["load"]?.jsonObject
        return ServerSnapshot(
            cpuPercent = obj["cpu"]?.asDouble() ?: 0.0,
            memUsedBytes = mem?.get("current")?.asLong() ?: 0L,
            memTotalBytes = mem?.get("total")?.asLong() ?: 0L,
            swapUsedBytes = swap?.get("current")?.asLong() ?: 0L,
            swapTotalBytes = swap?.get("total")?.asLong() ?: 0L,
            diskUsedBytes = disk?.get("current")?.asLong() ?: 0L,
            diskTotalBytes = disk?.get("total")?.asLong() ?: 0L,
            netUpBytes = netIO?.get("up")?.asLong() ?: 0L,
            netDownBytes = netIO?.get("down")?.asLong() ?: 0L,
            xrayState = xray?.get("state")?.asString().orEmpty(),
            xrayVersion = xray?.get("version")?.asString().orEmpty(),
            tcpCount = obj["tcpCount"]?.asLong() ?: 0L,
            load1 = load?.get("load1")?.asDouble() ?: 0.0,
            load5 = load?.get("load5")?.asDouble() ?: 0.0,
            load15 = load?.get("load15")?.asDouble() ?: 0.0,
        )
    }
}

data class ClientSummary(
    val email: String,
    val subId: String,
)

@Serializable
private data class ApiEnvelope(
    val success: Boolean = false,
    val msg: String? = null,
    val obj: JsonElement? = null,
)

private fun JsonElement.asDouble(): Double? =
    (this as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonElement.asLong(): Long? =
    (this as? JsonPrimitive)?.content?.toLongOrNull()
        ?: (this as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

private fun JsonElement.asString(): String? =
    (this as? JsonPrimitive)?.let { if (it.isString) it.content else it.content }
