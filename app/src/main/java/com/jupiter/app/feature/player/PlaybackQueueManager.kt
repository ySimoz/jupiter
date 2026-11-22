package com.jupiter.app.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class QueueMediaItem(
    val uri: String,
    val title: String,
    val isNetworkStream: Boolean = false,
    val headersJson: String? = null,
    val durationMs: Long = 0L,
)

enum class QueueSourceKind {
    SINGLE,
    FOLDER,
    PLAYLIST,
}

data class PlaybackQueueState(
    val items: List<QueueMediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val sourceTitle: String = "",
    val sourceKind: QueueSourceKind = QueueSourceKind.SINGLE,
) {
    val currentItem: QueueMediaItem?
        get() = items.getOrNull(currentIndex)

    val nextItem: QueueMediaItem?
        get() = if (currentIndex in 0 until items.lastIndex) items[currentIndex + 1] else null

    val previousItem: QueueMediaItem?
        get() = if (currentIndex in 1..items.lastIndex) items[currentIndex - 1] else null

    val hasNext: Boolean
        get() = nextItem != null

    val hasPrevious: Boolean
        get() = previousItem != null
}

@Singleton
class PlaybackQueueManager @Inject constructor() {
    private val lock = Any()
    private val _state = MutableStateFlow(PlaybackQueueState())
    val state: StateFlow<PlaybackQueueState> = _state.asStateFlow()

    fun setQueue(
        items: List<QueueMediaItem>,
        startIndex: Int,
        sourceTitle: String,
        sourceKind: QueueSourceKind,
    ) {
        val snapshot = items.toList()
        val resolvedIndex = if (snapshot.isEmpty()) -1 else startIndex.coerceIn(0, snapshot.lastIndex)
        synchronized(lock) {
            _state.value = PlaybackQueueState(
                items = snapshot,
                currentIndex = resolvedIndex,
                sourceTitle = sourceTitle,
                sourceKind = sourceKind,
            )
        }
    }

    fun setSingle(item: QueueMediaItem) {
        synchronized(lock) {
            _state.value = PlaybackQueueState(
                items = listOf(item),
                currentIndex = 0,
                sourceTitle = item.title,
                sourceKind = QueueSourceKind.SINGLE,
            )
        }
    }

    fun moveToIndex(index: Int): QueueMediaItem? {
        synchronized(lock) {
            val current = _state.value
            if (index !in current.items.indices) return null
            val target = current.items[index]
            _state.value = current.copy(currentIndex = index)
            return target
        }
    }

    fun moveNext(): QueueMediaItem? {
        synchronized(lock) {
            val current = _state.value
            if (!current.hasNext) return null
            val nextIndex = current.currentIndex + 1
            val target = current.items[nextIndex]
            _state.value = current.copy(currentIndex = nextIndex)
            return target
        }
    }

    fun movePrevious(): QueueMediaItem? {
        synchronized(lock) {
            val current = _state.value
            if (!current.hasPrevious) return null
            val prevIndex = current.currentIndex - 1
            val target = current.items[prevIndex]
            _state.value = current.copy(currentIndex = prevIndex)
            return target
        }
    }

    fun clear() {
        synchronized(lock) {
            _state.value = PlaybackQueueState()
        }
    }
}
