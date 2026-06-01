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
)
