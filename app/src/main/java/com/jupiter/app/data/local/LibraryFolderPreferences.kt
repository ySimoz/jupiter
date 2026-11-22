package com.jupiter.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

interface LibraryFolderPreferences {
    val hiddenFolders: Flow<Set<String>>
    val removedFolders: Flow<Set<String>>
    val customFolderUris: Flow<Set<String>>

    fun observeRemovedFolders(): Flow<Set<String>> = removedFolders
    suspend fun setFolderHidden(folderName: String, hidden: Boolean)
    suspend fun setFolderRemoved(folderName: String, removed: Boolean)
    suspend fun addCustomFolderUri(treeUri: String)
    suspend fun removeCustomFolderUri(treeUri: String)
}

class DataStoreLibraryFolderPreferences(
    private val dataStore: DataStore<Preferences>,
) : LibraryFolderPreferences {

    override val hiddenFolders: Flow<Set<String>> = observeStringSet(Keys.HIDDEN_FOLDERS)

    override val removedFolders: Flow<Set<String>> = observeStringSet(Keys.REMOVED_FOLDERS)

    override val customFolderUris: Flow<Set<String>> = observeStringSet(Keys.CUSTOM_FOLDER_URIS)

    override suspend fun setFolderHidden(folderName: String, hidden: Boolean) {
        updateStringSet(Keys.HIDDEN_FOLDERS, folderName, add = hidden)
    }

    override suspend fun setFolderRemoved(folderName: String, removed: Boolean) {
        updateStringSet(Keys.REMOVED_FOLDERS, folderName, add = removed)
    }

    override suspend fun addCustomFolderUri(treeUri: String) {
        updateStringSet(Keys.CUSTOM_FOLDER_URIS, treeUri, add = true)
    }

    override suspend fun removeCustomFolderUri(treeUri: String) {
        updateStringSet(Keys.CUSTOM_FOLDER_URIS, treeUri, add = false)
    }

    private fun observeStringSet(key: Preferences.Key<Set<String>>): Flow<Set<String>> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs -> prefs[key].orEmpty() }

    private suspend fun updateStringSet(
        key: Preferences.Key<Set<String>>,
        value: String,
        add: Boolean,
    ) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (add) current + normalized else current - normalized
        }
    }

    private object Keys {
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val REMOVED_FOLDERS = stringSetPreferencesKey("removed_folders")
        val CUSTOM_FOLDER_URIS = stringSetPreferencesKey("custom_folder_uris")
    }
}

class InMemoryLibraryFolderPreferences(
    initialHiddenFolders: Set<String> = emptySet(),
    initialRemovedFolders: Set<String> = emptySet(),
    initialCustomFolderUris: Set<String> = emptySet(),
) : LibraryFolderPreferences {

    private val _hiddenFolders = MutableStateFlow(initialHiddenFolders)
    override val hiddenFolders: Flow<Set<String>> = _hiddenFolders.asStateFlow()

    private val _removedFolders = MutableStateFlow(initialRemovedFolders)
    override val removedFolders: Flow<Set<String>> = _removedFolders.asStateFlow()

    private val _customFolderUris = MutableStateFlow(initialCustomFolderUris)
    override val customFolderUris: Flow<Set<String>> = _customFolderUris.asStateFlow()

    override suspend fun setFolderHidden(folderName: String, hidden: Boolean) {
        updateSet(_hiddenFolders, folderName, add = hidden)
    }

    override suspend fun setFolderRemoved(folderName: String, removed: Boolean) {
        updateSet(_removedFolders, folderName, add = removed)
    }

    override suspend fun addCustomFolderUri(treeUri: String) {
        updateSet(_customFolderUris, treeUri, add = true)
    }

    override suspend fun removeCustomFolderUri(treeUri: String) {
        updateSet(_customFolderUris, treeUri, add = false)
    }

    private fun updateSet(
        target: MutableStateFlow<Set<String>>,
        value: String,
        add: Boolean,
    ) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        target.update { current ->
            if (add) current + normalized else current - normalized
        }
    }
}
