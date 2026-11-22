package com.jupiter.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class HwDecMode(val mpvValue: String) {
    AUTO("mediacodec-copy"),
    MEDIACODEC_COPY("mediacodec-copy"),
    MEDIACODEC("mediacodec"),
    SOFTWARE("no")
}

data class VideoTrack(
    val id: Int,
    val title: String? = null,
    val lang: String? = null,
    val codec: String? = null,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
    val isExternal: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null
)

data class AudioTrack(
    val id: Int,
    val title: String? = null,
    val lang: String? = null,
    val codec: String? = null,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
    val isExternal: Boolean = false,
    val channelCount: Int? = null,
    val sampleRate: Int? = null
)

data class SubtitleTrack(
    val id: Int,
    val title: String? = null,
    val lang: String? = null,
    val codec: String? = null,
    val isSelected: Boolean = false,
    val isDefault: Boolean = false,
    val isExternal: Boolean = false
)

data class Chapter(
    val index: Int,
    val title: String? = null,
    val timeSec: Double = 0.0
)

data class ParsedTrackList(
    val videoTracks: List<VideoTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList()
)

object MpvJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTrackList(rawJson: String?): ParsedTrackList {
        val array = parseArrayOrNull(rawJson) ?: return ParsedTrackList()
        val videoTracks = mutableListOf<VideoTrack>()
        val audioTracks = mutableListOf<AudioTrack>()
        val subtitleTracks = mutableListOf<SubtitleTrack>()

        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val type = obj.string("type") ?: continue
            val id = obj.int("id") ?: continue
            val title = obj.string("title")
            val lang = obj.string("lang")
            val codec = obj.string("codec")
            val isSelected = obj.bool("selected") ?: false
            val isDefault = obj.bool("default") ?: false
            val isExternal = obj.bool("external") ?: false

            when (type) {
                "video" -> videoTracks += VideoTrack(
                    id = id,
                    title = title,
                    lang = lang,
                    codec = codec,
                    isSelected = isSelected,
                    isDefault = isDefault,
                    isExternal = isExternal,
                    width = obj.int("demux-w") ?: obj.int("w") ?: obj.int("width"),
                    height = obj.int("demux-h") ?: obj.int("h") ?: obj.int("height"),
                    fps = obj.double("demux-fps") ?: obj.double("fps")
                )

                "audio" -> audioTracks += AudioTrack(
                    id = id,
                    title = title,
                    lang = lang,
                    codec = codec,
                    isSelected = isSelected,
                    isDefault = isDefault,
                    isExternal = isExternal,
                    channelCount = obj.int("demux-channel-count")
                        ?: obj.int("audio-channels")
                        ?: obj.int("channel-count"),
                    sampleRate = obj.int("demux-samplerate") ?: obj.int("samplerate")
                )

                "sub" -> subtitleTracks += SubtitleTrack(
                    id = id,
                    title = title,
                    lang = lang,
                    codec = codec,
                    isSelected = isSelected,
                    isDefault = isDefault,
                    isExternal = isExternal
                )
            }
        }

        return ParsedTrackList(
            videoTracks = videoTracks,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    fun parseChapterList(rawJson: String?): List<Chapter> {
        val array = parseArrayOrNull(rawJson) ?: return emptyList()
        val chapters = mutableListOf<Chapter>()

        array.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return@forEachIndexed
            val timeSec = obj.double("time") ?: return@forEachIndexed
            chapters += Chapter(
                index = index,
                title = obj.string("title"),
                timeSec = timeSec
            )
        }
        return chapters
    }

    private fun parseArrayOrNull(rawJson: String?): JsonArray? {
        if (rawJson.isNullOrBlank() || rawJson.trim() == "null") return null
        return runCatching { json.parseToJsonElement(rawJson) as? JsonArray }.getOrNull()
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        primitive(key)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    private fun JsonObject.double(key: String): Double? =
        primitive(key)?.doubleOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        primitive(key)?.booleanOrNull
}
