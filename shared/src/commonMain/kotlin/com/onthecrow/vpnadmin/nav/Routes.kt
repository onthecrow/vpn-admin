package com.onthecrow.vpnadmin.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object ConfigsListRoute : Route

@Serializable
data class ConfigDetailRoute(val id: String) : Route

@Serializable
data class VpnConfigEditRoute(
    val parentConfigId: String,
    val vpnConfigId: String?,
) : Route

@Serializable
data object ServersListRoute : Route

@Serializable
data class ServerEditRoute(val id: String?) : Route

@Serializable
data class ServerDetailRoute(val id: String) : Route

enum class RootTab(val label: String) {
    Configs("Configs"),
    Servers("Servers"),
}
