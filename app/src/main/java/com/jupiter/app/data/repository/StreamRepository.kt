package com.jupiter.app.data.repository

import com.jupiter.app.data.local.NetworkStreamDao
import com.jupiter.app.data.local.NetworkStreamEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

@Serializable
data class StreamHeaderConfig(
    val userAgent: String? = null,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    fun hasAny(): Boolean =
        !userAgent.isNullOrBlank() ||
            !referer.isNullOrBlank() ||
            headers.any { (key, _) -> key.isNotBlank() }

    fun toJsonOrNull(): String? {
        val sanitized = sanitized()
        if (!sanitized.hasAny()) return null
        return jsonCodec.encodeToString(sanitized)
    }

    private fun sanitized(): StreamHeaderConfig = StreamHeaderConfig(
        userAgent = userAgent?.let(::sanitizeHeaderValue)?.takeIf { it.isNotBlank() },
        referer = referer?.let(::sanitizeHeaderValue)?.takeIf { it.isNotBlank() },
        headers = sanitizeHeadersMap(headers),
    )

    companion object {
        private val jsonCodec = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun sanitizeHeaderValue(raw: String): String =
            raw.replace("\r", "").replace("\n", "").trim()

        fun fromJson(json: String?): StreamHeaderConfig {
            if (json.isNullOrBlank()) return StreamHeaderConfig()
            val element = runCatching { jsonCodec.parseToJsonElement(json) }.getOrNull()
            val obj = element as? JsonObject ?: return StreamHeaderConfig()

            val isStructured = obj.containsKey("userAgent") ||
                obj.containsKey("referer") ||
                (obj["headers"] is JsonObject)

            if (isStructured) {
                return runCatching {
                    jsonCodec.decodeFromString<StreamHeaderConfig>(json).sanitized()
                }.getOrDefault(StreamHeaderConfig())
            }

            // Fallback for flat key-value JSON objects.
            var ua: String? = null
            var ref: String? = null
            val custom = linkedMapOf<String, String>()
            for ((rawKey, value) in obj) {
                val strVal = (value as? JsonPrimitive)?.contentOrNull ?: continue
                val cleanKey = sanitizeHeaderValue(rawKey)
                if (cleanKey.isBlank()) continue
                val cleanVal = sanitizeHeaderValue(strVal)
                when {
                    cleanKey.equals("user-agent", ignoreCase = true) -> {
                        if (cleanVal.isNotBlank()) ua = cleanVal
                    }

                    cleanKey.equals("referer", ignoreCase = true) ||
                        cleanKey.equals("referrer", ignoreCase = true) -> {
                        if (cleanVal.isNotBlank()) ref = cleanVal
                    }

                    else -> custom[cleanKey] = cleanVal
                }
            }
            return StreamHeaderConfig(
                userAgent = ua,
                referer = ref,
                headers = custom,
            )
        }

        fun parseHeadersMap(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            val obj = runCatching { jsonCodec.parseToJsonElement(json) as? JsonObject }.getOrNull()
                ?: return emptyMap()
            val result = linkedMapOf<String, String>()
            for ((rawKey, value) in obj) {
                val strVal = (value as? JsonPrimitive)?.contentOrNull ?: continue
                val cleanKey = sanitizeHeaderValue(rawKey)
                if (cleanKey.isBlank()) continue
                result[cleanKey] = sanitizeHeaderValue(strVal)
            }
            return result
        }

        fun encodeHeadersMap(headers: Map<String, String>): String? {
            val cleaned = sanitizeHeadersMap(headers)
            if (cleaned.isEmpty()) return null
            return jsonCodec.encodeToString(cleaned)
        }

        private fun sanitizeHeadersMap(headers: Map<String, String>): Map<String, String> {
            if (headers.isEmpty()) return emptyMap()
            val cleaned = linkedMapOf<String, String>()
            for ((rawKey, rawValue) in headers) {
                val cleanKey = sanitizeHeaderValue(rawKey)
                if (cleanKey.isBlank()) continue
                cleaned[cleanKey] = sanitizeHeaderValue(rawValue)
            }
            return cleaned
        }
    }
}

object StreamUrlValidator {
    val ALLOWED_SCHEMES: Set<String> = setOf(
        "http",
        "https",
        "rtsp",
        "rtmp",
        "mms",
        "udp",
        "rtp",
        "ftp",
        "smb",
    )

    sealed interface ValidationResult {
        data class Valid(
            val normalizedUrl: String,
            val scheme: String,
            val host: String,
        ) : ValidationResult

        data class Invalid(val reason: String) : ValidationResult
    }

    fun validate(rawUrl: String): ValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Enter a stream URL")
        }
        if (trimmed.any { it == '\r' || it == '\n' || it.isWhitespace() }) {
            return ValidationResult.Invalid("URL cannot contain spaces or line breaks")
        }

        val schemeSeparatorIdx = trimmed.indexOf("://")
        if (schemeSeparatorIdx <= 0) {
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0) {
                val maybeScheme = trimmed.substring(0, colonIdx)
                if (maybeScheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
                    return ValidationResult.Invalid("Unsupported URL scheme: $maybeScheme")
                }
            }
            return ValidationResult.Invalid("URL must start with a scheme like https:// or rtsp://")
        }

        val rawScheme = trimmed.substring(0, schemeSeparatorIdx)
        val scheme = rawScheme.lowercase(Locale.US)
        if (scheme !in ALLOWED_SCHEMES) {
            return ValidationResult.Invalid("Unsupported URL scheme: $rawScheme")
        }

        val normalized = if (rawScheme == scheme) {
            trimmed
        } else {
            scheme + trimmed.substring(schemeSeparatorIdx)
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return ValidationResult.Invalid("Malformed stream URL")

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: extractHostFromAuthority(uri.rawAuthority)
            ?: return ValidationResult.Invalid("Stream URL must include a host")

        if (!hasValidPortIfPresent(uri.rawAuthority)) {
            return ValidationResult.Invalid("Stream URL has an invalid port")
        }

        return ValidationResult.Valid(
            normalizedUrl = normalized,
            scheme = scheme,
            host = host,
        )
    }

    fun deriveDisplayTitle(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""

        val clean = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
        val schemeSep = clean.indexOf("://")
        if (schemeSep != -1) {
            val authorityStart = schemeSep + 3
            val pathStart = clean.indexOf('/', authorityStart)
            if (pathStart != -1 && pathStart < clean.length - 1) {
                val segment = clean.substringAfterLast('/')
                if (segment.isNotBlank()) {
                    val decoded = runCatching {
                        URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name())
                    }.getOrDefault(segment)
                    if (decoded.isNotBlank()) return decoded
                }
            }
            val validated = validate(trimmed)
            if (validated is ValidationResult.Valid) {
                return validated.host
            }
            val authority = if (pathStart != -1) {
                clean.substring(authorityStart, pathStart)
            } else {
                clean.substring(authorityStart)
            }
            val hostFallback = extractHostFromAuthority(authority)
            if (!hostFallback.isNullOrBlank()) return hostFallback
        }

        val lastSegment = clean.substringAfterLast('/')
        return lastSegment.ifBlank { trimmed }
    }

    private fun extractHostFromAuthority(authority: String?): String? {
        if (authority.isNullOrBlank()) return null
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isBlank()) return null
        return if (hostPort.startsWith('[')) {
            val closeBracket = hostPort.indexOf(']')
            if (closeBracket > 1) hostPort.substring(0, closeBracket + 1) else null
        } else {
            hostPort.substringBefore(':').takeIf { it.isNotBlank() }
        }
    }

    private fun hasValidPortIfPresent(authority: String?): Boolean {
        if (authority.isNullOrBlank()) return true
        val hostPort = authority.substringAfterLast('@')
        val colonIdx = if (hostPort.startsWith('[')) {
            val closeBracket = hostPort.indexOf(']')
            if (closeBracket < 0) return false
            if (closeBracket + 1 < hostPort.length && hostPort[closeBracket + 1] == ':') {
                closeBracket + 1
            } else {
                -1
            }
        } else {
            hostPort.indexOf(':')
        }
        if (colonIdx < 0) return true
        val portStr = hostPort.substring(colonIdx + 1)
        val port = portStr.toIntOrNull() ?: return false
        return port in 1..65535
    }
}

interface StreamRepository {
    fun observeStreams(): Flow<List<NetworkStreamEntity>>
    suspend fun getStream(id: Long): NetworkStreamEntity?
    suspend fun saveStream(
        id: Long = 0L,
        title: String,
        url: String,
        userAgent: String? = null,
        referer: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
        isPinned: Boolean = false,
    ): Result<Long>

    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun recordPlayed(id: Long)
    suspend fun deleteStream(id: Long)
}

class DefaultStreamRepository(
    private val networkStreamDao: NetworkStreamDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamRepository {

    override fun observeStreams(): Flow<List<NetworkStreamEntity>> =
        networkStreamDao.observeAll().map { streams ->
            streams.sortedWith(STREAM_COMPARATOR)
        }

    override suspend fun getStream(id: Long): NetworkStreamEntity? =
        networkStreamDao.getById(id)

    override suspend fun saveStream(
        id: Long,
        title: String,
        url: String,
        userAgent: String?,
        referer: String?,
        customHeaders: Map<String, String>,
        isPinned: Boolean,
    ): Result<Long> {
        val validation = StreamUrlValidator.validate(url)
        if (validation is StreamUrlValidator.ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(validation.reason))
        }
        val normalizedUrl = (validation as StreamUrlValidator.ValidationResult.Valid).normalizedUrl

        val existing = if (id > 0L) networkStreamDao.getById(id) else null
        val resolvedTitle = title.trim().ifBlank {
            StreamUrlValidator.deriveDisplayTitle(normalizedUrl)
        }
        val cleanUserAgent = userAgent
            ?.let(StreamHeaderConfig::sanitizeHeaderValue)
            ?.takeIf { it.isNotBlank() }
        val cleanReferer = referer
            ?.let(StreamHeaderConfig::sanitizeHeaderValue)
            ?.takeIf { it.isNotBlank() }
        val encodedHeaders = StreamHeaderConfig.encodeHeadersMap(customHeaders)

        val entity = NetworkStreamEntity(
            id = id,
            title = resolvedTitle,
            url = normalizedUrl,
            userAgent = cleanUserAgent,
            referer = cleanReferer,
            customHeadersJson = encodedHeaders,
            isPinned = isPinned,
            createdAtEpochMs = existing?.createdAtEpochMs ?: clock(),
            lastPlayedEpochMs = existing?.lastPlayedEpochMs,
        )

        val rowId = networkStreamDao.upsert(entity)
        val effectiveId = if (id > 0L) id else rowId
        return Result.success(effectiveId)
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        networkStreamDao.setPinned(id, pinned)
    }

    override suspend fun recordPlayed(id: Long) {
        if (id > 0L) {
            networkStreamDao.updateLastPlayed(id, clock())
        }
    }

    override suspend fun deleteStream(id: Long) {
        networkStreamDao.deleteById(id)
    }

    private companion object {
        val STREAM_COMPARATOR: Comparator<NetworkStreamEntity> =
            compareByDescending<NetworkStreamEntity> { it.isPinned }
                .thenByDescending { it.lastPlayedEpochMs ?: it.createdAtEpochMs }
                .thenByDescending { it.id }
    }
}
