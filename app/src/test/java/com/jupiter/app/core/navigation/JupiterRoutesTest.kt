package com.jupiter.app.core.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JupiterRoutesTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `LibraryRoute round-trips through kotlinx serialization`() {
        val encoded = json.encodeToString(LibraryRoute)
        val decoded = json.decodeFromString<LibraryRoute>(encoded)
        assertEquals(LibraryRoute, decoded)
    }

    @Test
    fun `StreamsRoute round-trips through kotlinx serialization`() {
        val encoded = json.encodeToString(StreamsRoute)
        val decoded = json.decodeFromString<StreamsRoute>(encoded)
        assertEquals(StreamsRoute, decoded)
    }

    @Test
    fun `SettingsRoute round-trips through kotlinx serialization`() {
        val encoded = json.encodeToString(SettingsRoute)
        val decoded = json.decodeFromString<SettingsRoute>(encoded)
        assertEquals(SettingsRoute, decoded)
    }

    @Test
    fun `PlayerRoute preserves defaults and nullable headersJson`() {
        val route = PlayerRoute(mediaUri = "file:///storage/emulated/0/Movies/sample.mkv")
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<PlayerRoute>(encoded)

        assertEquals(route, decoded)
        assertEquals("", decoded.title)
        assertFalse(decoded.isNetworkStream)
        assertNull(decoded.headersJson)
    }

    @Test
    fun `PlayerRoute preserves all populated fields`() {
        val route = PlayerRoute(
            mediaUri = "https://example.com/live/master.m3u8",
            title = "Live Feed",
            isNetworkStream = true,
            headersJson = """{"User-Agent":"Jupiter/1.0","Referer":"https://example.com"}""",
        )
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<PlayerRoute>(encoded)

        assertEquals(route, decoded)
        assertEquals("Live Feed", decoded.title)
        assertTrue(decoded.isNetworkStream)
        assertEquals(route.headersJson, decoded.headersJson)
    }
}
