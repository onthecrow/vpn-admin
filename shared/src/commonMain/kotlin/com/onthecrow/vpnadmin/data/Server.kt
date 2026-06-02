package com.onthecrow.vpnadmin.data

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable

@Serializable
data class InboundSummary(
    val id: Long,
    val remark: String = "",
    val protocol: String = "",
    val port: Long = 0L,
)

@Serializable
data class Server(
    val id: String,
    val name: String = "",

    /** Full panel base URL: scheme + host + port + web base path. e.g.
     *  `https://onthecrow.tech:61805/wwV5oEImPhb9UuKV1l` (no trailing slash). */
    val panelBaseUrl: String = "",

    /** Bearer token from 3X-UI Settings → Security → API Token. */
    val apiToken: String = "",

    /** For self-signed certs or IP-based panels where the cert SAN doesn't match. */
    val skipTlsVerify: Boolean = false,

    /** Synced from `GET /panel/api/inbounds/options` on each verify. */
    val inbounds: List<InboundSummary> = emptyList(),

    /** Free-form per-server settings — future use. Empty by default. */
    val settings: Map<String, String> = emptyMap(),

    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Strips trailing slashes — what the HTTP client should prefix to API paths. */
    val normalizedBase: String get() = panelBaseUrl.trim().trimEnd('/')
}

interface ServerRepository {
    fun observeAll(): Flow<List<Server>>
    suspend fun get(id: String): Server?
    suspend fun create(server: Server)
    suspend fun update(server: Server)
    suspend fun delete(id: String)
}

@OptIn(ExperimentalTime::class)
class FirestoreServerRepository(
    private val firestore: FirebaseFirestore,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ServerRepository {

    private val collection get() = firestore.collection("servers")

    override fun observeAll(): Flow<List<Server>> =
        collection.snapshots.map { snap ->
            snap.documents.map { doc ->
                doc.data(ServerDto.serializer()).toModel(id = doc.id)
            }.sortedByDescending { it.updatedAt }
        }

    override suspend fun get(id: String): Server? {
        val doc = collection.document(id).get()
        if (!doc.exists) return null
        return doc.data(ServerDto.serializer()).toModel(id = doc.id)
    }

    override suspend fun create(server: Server) {
        val now = nowMs()
        val dto = server.copy(createdAt = now, updatedAt = now).toDto()
        collection.document(server.id).set(ServerDto.serializer(), dto)
    }

    override suspend fun update(server: Server) {
        val dto = server.copy(updatedAt = nowMs()).toDto()
        collection.document(server.id).set(ServerDto.serializer(), dto)
    }

    override suspend fun delete(id: String) {
        collection.document(id).delete()
    }
}

@Serializable
private data class ServerDto(
    val name: String = "",
    val panelBaseUrl: String = "",
    val apiToken: String = "",
    val skipTlsVerify: Boolean = false,
    val inbounds: List<InboundSummary> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

private fun ServerDto.toModel(id: String) = Server(
    id = id,
    name = name,
    panelBaseUrl = panelBaseUrl,
    apiToken = apiToken,
    skipTlsVerify = skipTlsVerify,
    inbounds = inbounds,
    settings = settings,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Server.toDto() = ServerDto(
    name = name,
    panelBaseUrl = panelBaseUrl,
    apiToken = apiToken,
    skipTlsVerify = skipTlsVerify,
    inbounds = inbounds,
    settings = settings,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
