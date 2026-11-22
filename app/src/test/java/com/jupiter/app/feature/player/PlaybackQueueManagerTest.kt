package com.jupiter.app.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackQueueManagerTest {

    private lateinit var manager: PlaybackQueueManager

    private val itemA = QueueMediaItem(uri = "content://media/1", title = "Episode 1", durationMs = 1_000L)
    private val itemB = QueueMediaItem(
        uri = "https://example.com/2.mp4",
        title = "Episode 2",
        isNetworkStream = true,
        headersJson = """{"User-Agent":"Jupiter"}""",
        durationMs = 2_000L,
    )
    private val itemC = QueueMediaItem(uri = "content://media/3", title = "Episode 3", durationMs = 3_000L)

    @Before
    fun setUp() {
        manager = PlaybackQueueManager()
    }

    @Test
    fun `initial state is empty with no current next or previous items`() {
        val state = manager.state.value
        assertTrue(state.items.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertEquals("", state.sourceTitle)
        assertEquals(QueueSourceKind.SINGLE, state.sourceKind)
        assertNull(state.currentItem)
        assertNull(state.nextItem)
        assertNull(state.previousItem)
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `setQueue clamps startIndex and handles empty list`() {
        manager.setQueue(
            items = listOf(itemA, itemB, itemC),
            startIndex = -5,
            sourceTitle = "Season 1",
            sourceKind = QueueSourceKind.FOLDER,
        )
        assertEquals(0, manager.state.value.currentIndex)
        assertEquals(itemA, manager.state.value.currentItem)
        assertEquals("Season 1", manager.state.value.sourceTitle)
        assertEquals(QueueSourceKind.FOLDER, manager.state.value.sourceKind)

        manager.setQueue(
            items = listOf(itemA, itemB, itemC),
            startIndex = 99,
            sourceTitle = "Favorites",
            sourceKind = QueueSourceKind.PLAYLIST,
        )
        assertEquals(2, manager.state.value.currentIndex)
        assertEquals(itemC, manager.state.value.currentItem)
        assertEquals(QueueSourceKind.PLAYLIST, manager.state.value.sourceKind)

        manager.setQueue(
            items = emptyList(),
            startIndex = 0,
            sourceTitle = "Empty",
            sourceKind = QueueSourceKind.FOLDER,
        )
        assertTrue(manager.state.value.items.isEmpty())
        assertEquals(-1, manager.state.value.currentIndex)
        assertNull(manager.state.value.currentItem)
        assertFalse(manager.state.value.hasNext)
        assertFalse(manager.state.value.hasPrevious)
    }

    @Test
    fun `setSingle sets single item at index zero`() {
        manager.setSingle(itemB)

        val state = manager.state.value
        assertEquals(listOf(itemB), state.items)
        assertEquals(0, state.currentIndex)
        assertEquals(QueueSourceKind.SINGLE, state.sourceKind)
        assertEquals("Episode 2", state.sourceTitle)
        assertEquals(itemB, state.currentItem)
        assertNull(state.nextItem)
        assertNull(state.previousItem)
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `computed properties reflect start middle end and out-of-range states`() {
        val atStart = PlaybackQueueState(items = listOf(itemA, itemB, itemC), currentIndex = 0)
        assertEquals(itemA, atStart.currentItem)
        assertNull(atStart.previousItem)
        assertEquals(itemB, atStart.nextItem)
        assertFalse(atStart.hasPrevious)
        assertTrue(atStart.hasNext)

        val atMiddle = PlaybackQueueState(items = listOf(itemA, itemB, itemC), currentIndex = 1)
        assertEquals(itemB, atMiddle.currentItem)
        assertEquals(itemA, atMiddle.previousItem)
        assertEquals(itemC, atMiddle.nextItem)
        assertTrue(atMiddle.hasPrevious)
        assertTrue(atMiddle.hasNext)

        val atEnd = PlaybackQueueState(items = listOf(itemA, itemB, itemC), currentIndex = 2)
        assertEquals(itemC, atEnd.currentItem)
        assertEquals(itemB, atEnd.previousItem)
        assertNull(atEnd.nextItem)
        assertTrue(atEnd.hasPrevious)
        assertFalse(atEnd.hasNext)

        val invalidNegative = PlaybackQueueState(items = listOf(itemA, itemB), currentIndex = -1)
        assertNull(invalidNegative.currentItem)
        assertNull(invalidNegative.previousItem)
        assertNull(invalidNegative.nextItem)
        assertFalse(invalidNegative.hasPrevious)
        assertFalse(invalidNegative.hasNext)

        val invalidHigh = PlaybackQueueState(items = listOf(itemA, itemB), currentIndex = 5)
        assertNull(invalidHigh.currentItem)
        assertNull(invalidHigh.previousItem)
        assertNull(invalidHigh.nextItem)
        assertFalse(invalidHigh.hasPrevious)
        assertFalse(invalidHigh.hasNext)
    }

    @Test
    fun `moveNext and movePrevious step through queue and stop at boundaries`() {
        manager.setQueue(
            items = listOf(itemA, itemB, itemC),
            startIndex = 0,
            sourceTitle = "Folder",
            sourceKind = QueueSourceKind.FOLDER,
        )

        assertNull(manager.movePrevious())
        assertEquals(0, manager.state.value.currentIndex)

        assertEquals(itemB, manager.moveNext())
        assertEquals(1, manager.state.value.currentIndex)
        assertTrue(manager.state.value.hasPrevious)
        assertTrue(manager.state.value.hasNext)

        assertEquals(itemC, manager.moveNext())
        assertEquals(2, manager.state.value.currentIndex)
        assertFalse(manager.state.value.hasNext)

        assertNull(manager.moveNext())
        assertEquals(2, manager.state.value.currentIndex)

        assertEquals(itemB, manager.movePrevious())
        assertEquals(1, manager.state.value.currentIndex)

        assertEquals(itemA, manager.movePrevious())
        assertEquals(0, manager.state.value.currentIndex)
    }

    @Test
    fun `moveToIndex updates index when valid and ignores out-of-bounds`() {
        assertNull(manager.moveToIndex(0))

        manager.setQueue(
            items = listOf(itemA, itemB, itemC),
            startIndex = 0,
            sourceTitle = "Playlist",
            sourceKind = QueueSourceKind.PLAYLIST,
        )

        assertEquals(itemC, manager.moveToIndex(2))
        assertEquals(2, manager.state.value.currentIndex)

        assertNull(manager.moveToIndex(-1))
        assertNull(manager.moveToIndex(3))
        assertEquals(2, manager.state.value.currentIndex)
    }

    @Test
    fun `clear resets queue back to empty default state`() {
        manager.setQueue(
            items = listOf(itemA, itemB),
            startIndex = 1,
            sourceTitle = "Season",
            sourceKind = QueueSourceKind.FOLDER,
        )

        manager.clear()

        assertEquals(PlaybackQueueState(), manager.state.value)
        assertNull(manager.moveNext())
        assertNull(manager.movePrevious())
    }
}
