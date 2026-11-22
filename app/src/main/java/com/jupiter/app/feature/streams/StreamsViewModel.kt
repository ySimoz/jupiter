package com.jupiter.app.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.app.data.local.NetworkStreamEntity
import com.jupiter.app.data.repository.StreamHeaderConfig
import com.jupiter.app.data.repository.StreamRepository
import com.jupiter.app.data.repository.StreamUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeaderDraftRow(
    val id: Int,
    val key: String = "",
    val value: String = "",
)

data class StreamEditorState(
    val editingId: Long = 0L,
    val title: String = "",
    val url: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val customHeaders: List<HeaderDraftRow> = emptyList(),
    val isPinned: Boolean = false,
    val errorMessage: String? = null,
)

data class StreamLaunchRequest(
    val url: String,
    val title: String,
    val headersJson: String?,
)

data class StreamsUiState(
    val quickUrl: String = "",
    val quickUrlError: String? = null,
    val pinnedStreams: List<NetworkStreamEntity> = emptyList(),
    val savedStreams: List<NetworkStreamEntity> = emptyList(),
    val editor: StreamEditorState? = null,
)

@HiltViewModel
class StreamsViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private var nextHeaderRowId = 1

    init {
        viewModelScope.launch {
            streamRepository.observeStreams().collect { streams ->
                val pinned = streams.filter { it.isPinned }
                val unpinned = streams.filterNot { it.isPinned }
                _uiState.update { state ->
                    state.copy(
                        pinnedStreams = pinned,
                        savedStreams = unpinned,
                    )
                }
            }
        }
    }

    fun onQuickUrlChange(value: String) {
        _uiState.update { state ->
            state.copy(
                quickUrl = value,
                quickUrlError = null,
            )
        }
    }

    fun onPasteQuickUrl(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(quickUrlError = "Clipboard is empty") }
        } else {
            _uiState.update {
                it.copy(
                    quickUrl = trimmed,
                    quickUrlError = null,
                )
            }
        }
    }

    fun submitQuickPlay(): StreamLaunchRequest? {
        return when (val result = StreamUrlValidator.validate(_uiState.value.quickUrl)) {
            is StreamUrlValidator.ValidationResult.Invalid -> {
                _uiState.update { it.copy(quickUrlError = result.reason) }
                null
            }
            is StreamUrlValidator.ValidationResult.Valid -> {
                _uiState.update {
                    it.copy(
                        quickUrl = result.normalizedUrl,
                        quickUrlError = null,
                    )
                }
                StreamLaunchRequest(
                    url = result.normalizedUrl,
                    title = StreamUrlValidator.deriveDisplayTitle(result.normalizedUrl),
                    headersJson = null,
                )
            }
        }
    }

    fun openAddDialog(prefillUrl: String = "") {
        _uiState.update { state ->
            state.copy(
                editor = StreamEditorState(
                    url = prefillUrl.trim(),
                ),
            )
        }
    }

    fun openEditDialog(stream: NetworkStreamEntity) {
        val parsedHeaders = StreamHeaderConfig.parseHeadersMap(stream.customHeadersJson)
        val rows = parsedHeaders.entries.map { (key, value) ->
            HeaderDraftRow(
                id = nextHeaderRowId++,
                key = key,
                value = value,
            )
        }
        _uiState.update { state ->
            state.copy(
                editor = StreamEditorState(
                    editingId = stream.id,
                    title = stream.title,
                    url = stream.url,
                    userAgent = stream.userAgent.orEmpty(),
                    referer = stream.referer.orEmpty(),
                    customHeaders = rows,
                    isPinned = stream.isPinned,
                    errorMessage = null,
                ),
            )
        }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    fun updateEditor(
        title: String? = null,
        url: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        isPinned: Boolean? = null,
    ) {
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            state.copy(
                editor = current.copy(
                    title = title ?: current.title,
                    url = url ?: current.url,
                    userAgent = userAgent ?: current.userAgent,
                    referer = referer ?: current.referer,
                    isPinned = isPinned ?: current.isPinned,
                    errorMessage = null,
                ),
            )
        }
    }

    fun addHeaderRow() {
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            state.copy(
                editor = current.copy(
                    customHeaders = current.customHeaders + HeaderDraftRow(id = nextHeaderRowId++),
                    errorMessage = null,
                ),
            )
        }
    }

    fun updateHeaderRow(id: Int, key: String, value: String) {
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            val updatedRows = current.customHeaders.map { row ->
                if (row.id == id) row.copy(key = key, value = value) else row
            }
            state.copy(
                editor = current.copy(
                    customHeaders = updatedRows,
                    errorMessage = null,
                ),
            )
        }
    }

    fun removeHeaderRow(id: Int) {
        _uiState.update { state ->
            val current = state.editor ?: return@update state
            state.copy(
                editor = current.copy(
                    customHeaders = current.customHeaders.filterNot { it.id == id },
                ),
            )
        }
    }

    fun saveEditor() {
        val current = _uiState.value.editor ?: return
        val validation = StreamUrlValidator.validate(current.url)
        if (validation is StreamUrlValidator.ValidationResult.Invalid) {
            _uiState.update { state ->
                val editor = state.editor ?: return@update state
                state.copy(editor = editor.copy(errorMessage = validation.reason))
            }
            return
        }

        val validUrl = (validation as StreamUrlValidator.ValidationResult.Valid).normalizedUrl
        val resolvedTitle = current.title.trim().ifEmpty {
            StreamUrlValidator.deriveDisplayTitle(validUrl)
        }
        val headersMap = linkedMapOf<String, String>()
        current.customHeaders.forEach { row ->
            val cleanKey = row.key.trim()
            if (cleanKey.isNotEmpty()) {
                headersMap[cleanKey] = row.value.trim()
            }
        }

        viewModelScope.launch {
            val result = streamRepository.saveStream(
                id = current.editingId,
                title = resolvedTitle,
                url = validUrl,
                userAgent = current.userAgent.trim().ifEmpty { null },
                referer = current.referer.trim().ifEmpty { null },
                customHeaders = headersMap,
                isPinned = current.isPinned,
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(editor = null) }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val editor = state.editor ?: return@update state
                        state.copy(
                            editor = editor.copy(
                                errorMessage = error.message ?: "Unable to save stream",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun togglePin(stream: NetworkStreamEntity) {
        viewModelScope.launch {
            streamRepository.setPinned(stream.id, !stream.isPinned)
        }
    }

    fun deleteStream(id: Long) {
        viewModelScope.launch {
            streamRepository.deleteStream(id)
        }
    }

    fun prepareLaunch(stream: NetworkStreamEntity): StreamLaunchRequest {
        viewModelScope.launch {
            streamRepository.recordPlayed(stream.id)
        }
        val headers = StreamHeaderConfig.parseHeadersMap(stream.customHeadersJson)
        val headersJson = StreamHeaderConfig(
            userAgent = stream.userAgent,
            referer = stream.referer,
            headers = headers,
        ).toJsonOrNull()
        val title = stream.title.trim().ifEmpty {
            StreamUrlValidator.deriveDisplayTitle(stream.url)
        }
        return StreamLaunchRequest(
            url = stream.url,
            title = title,
            headersJson = headersJson,
        )
    }
}
