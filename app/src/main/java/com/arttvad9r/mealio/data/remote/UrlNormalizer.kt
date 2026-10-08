package com.arttvad9r.mealio.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Normalises a user-entered Mealie server address into a valid Retrofit base URL. */
object UrlNormalizer {

    sealed interface Result {
        data class Ok(val normalized: String, val baseUrl: String) : Result
        data object Invalid : Result
    }

    /**
     * Accepts `host`, `host:port`, `http://host:port`, `https://host/prefix`.
     * A missing scheme defaults to `http` (self-hosted LAN / Tailscale servers
     * are usually plain HTTP). Trailing slashes are trimmed; a sub-path is kept.
     */
    fun normalize(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Invalid

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("://") -> return Result.Invalid
            else -> "http://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull() ?: return Result.Invalid
        if (parsed.host.isBlank()) return Result.Invalid

        val normalized = parsed.toString().trimEnd('/')
        val base = if (normalized.endsWith("/")) normalized else "$normalized/"
        return Result.Ok(normalized = normalized, baseUrl = base)
    }
}
