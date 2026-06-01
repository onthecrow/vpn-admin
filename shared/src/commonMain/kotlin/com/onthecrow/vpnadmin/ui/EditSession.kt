package com.onthecrow.vpnadmin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onthecrow.vpnadmin.data.TopLevelConfig

/**
 * In-memory draft of the top-level config currently being edited. Shared between
 * ConfigDetailScreen and VpnConfigEditScreen so the latter can mutate the parent
 * without round-tripping through Firestore. Persisted to Firestore on explicit Save.
 */
class EditSession {
    var draft by mutableStateOf<TopLevelConfig?>(null)
        private set

    fun load(config: TopLevelConfig) {
        draft = config
    }

    fun update(transform: (TopLevelConfig) -> TopLevelConfig) {
        draft = draft?.let(transform)
    }

    fun clear() {
        draft = null
    }
}
