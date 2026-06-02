package com.onthecrow.vpnadmin.data

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable

const val DEFAULT_TEMPLATE_ID = "default"

@Serializable
data class SubscriptionTemplate(
    val id: String = DEFAULT_TEMPLATE_ID,
    val slots: List<TemplateSlot> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class TemplateSlot(
    val id: String,
    val serverId: String? = null,
    val configs: List<TemplateConfigEntry> = emptyList(),
)

@Serializable
data class TemplateConfigEntry(
    val id: String,
    val name: String = "",
    val location: String = "",
    val type: TemplateConfigType = TemplateConfigType.DIRECT,
    val protocol: TemplateConfigProtocol = TemplateConfigProtocol.VLESS,
    /** Inbound id on the selected server. Cleared when the slot's server changes. */
    val inboundId: Long? = null,
)

@Serializable
enum class TemplateConfigType { DIRECT, CASCADE }

@Serializable
enum class TemplateConfigProtocol { VLESS, HYSTERIA2 }

interface TemplateRepository {
    fun observe(): Flow<SubscriptionTemplate>
    suspend fun save(template: SubscriptionTemplate)
}

@OptIn(ExperimentalTime::class)
class FirestoreTemplateRepository(
    private val firestore: FirebaseFirestore,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TemplateRepository {

    private val doc get() = firestore.collection("templates").document(DEFAULT_TEMPLATE_ID)

    override fun observe(): Flow<SubscriptionTemplate> =
        doc.snapshots.map { snap ->
            if (!snap.exists) SubscriptionTemplate()
            else snap.data(SubscriptionTemplate.serializer())
        }

    override suspend fun save(template: SubscriptionTemplate) {
        val now = nowMs()
        val withTs = template.copy(
            createdAt = if (template.createdAt == 0L) now else template.createdAt,
            updatedAt = now,
        )
        doc.set(SubscriptionTemplate.serializer(), withTs)
    }
}
