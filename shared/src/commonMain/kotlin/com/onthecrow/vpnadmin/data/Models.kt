package com.onthecrow.vpnadmin.data

import kotlinx.serialization.Serializable

@Serializable
data class TopLevelConfig(
    val id: String,
    val name: String = "",
    val configs: List<VpnConfig> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class VpnConfig(
    val id: String,
    val location: String = "",
    val name: String = "",
    val url: String = "",
    /**
     * Routing mode for this client config. Populated from the subscription template when a
     * new subscription is created; can also be edited manually. Old documents without this
     * field default to [TemplateConfigType.DIRECT].
     *
     * Consumers (e.g. OnthecrowVpn client app) read this value to label each entry in the
     * client list.
     */
    val type: TemplateConfigType = TemplateConfigType.DIRECT,
)
