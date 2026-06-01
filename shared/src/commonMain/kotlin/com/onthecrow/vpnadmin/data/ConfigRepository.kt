package com.onthecrow.vpnadmin.data

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

interface ConfigRepository {
    fun observeAll(): Flow<List<TopLevelConfig>>
    suspend fun get(id: String): TopLevelConfig?
    suspend fun create(config: TopLevelConfig)
    suspend fun update(config: TopLevelConfig)
    suspend fun delete(id: String)
}

@OptIn(ExperimentalTime::class)
class FirestoreConfigRepository(
    private val firestore: FirebaseFirestore,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ConfigRepository {

    private val collection get() = firestore.collection("configs")

    override fun observeAll(): Flow<List<TopLevelConfig>> =
        collection.snapshots.map { snap ->
            snap.documents.map { doc ->
                val dto = doc.data(TopLevelConfigDto.serializer())
                dto.toModel(id = doc.id)
            }.sortedByDescending { it.updatedAt }
        }

    override suspend fun get(id: String): TopLevelConfig? {
        val doc = collection.document(id).get()
        if (!doc.exists) return null
        return doc.data(TopLevelConfigDto.serializer()).toModel(id = doc.id)
    }

    override suspend fun create(config: TopLevelConfig) {
        val now = nowMs()
        val dto = config.copy(createdAt = now, updatedAt = now).toDto()
        collection.document(config.id).set(TopLevelConfigDto.serializer(), dto)
    }

    override suspend fun update(config: TopLevelConfig) {
        val dto = config.copy(updatedAt = nowMs()).toDto()
        collection.document(config.id).set(TopLevelConfigDto.serializer(), dto)
    }

    override suspend fun delete(id: String) {
        collection.document(id).delete()
    }
}

@OptIn(ExperimentalUuidApi::class)
fun newConfigId(): String =
    Uuid.random().toHexString().take(20)

@Serializable
private data class TopLevelConfigDto(
    val name: String = "",
    val configs: List<VpnConfig> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

private fun TopLevelConfigDto.toModel(id: String) = TopLevelConfig(
    id = id,
    name = name,
    configs = configs,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun TopLevelConfig.toDto() = TopLevelConfigDto(
    name = name,
    configs = configs,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
