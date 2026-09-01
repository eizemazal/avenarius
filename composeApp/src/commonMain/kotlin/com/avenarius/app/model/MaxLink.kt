package com.avenarius.app.model

/**
 * A link that belongs to Max and should be handled inside the app rather than
 * handed to the browser.
 */
sealed interface MaxLink {
    /**
     * An invite to a chat, channel or user profile — `https://max.ru/<token>`.
     *
     * The URL doesn't say which of the three it is (the protocol's own `linkType` is
     * CHAT / CHANNEL / USER), so the server is asked to resolve it.
     */
    data class Invite(
        /** The link as the server wants it, without scheme or query. */
        val link: String,
        /** The last path segment, for matching against a chat we already know. */
        val token: String,
    ) : MaxLink

    /** A call invite — `https://max.ru/joincall/<id>`. Calls aren't implemented yet. */
    data class JoinCall(
        val id: String,
    ) : MaxLink
}

/**
 * Recognises a Max link, or returns null for anything else (which belongs in the
 * browser).
 *
 * Deliberately lenient about the scheme and any `www.`: links arrive from messages,
 * QR codes and other apps, and a link that looks like Max but isn't recognised
 * would silently leave the app.
 */
fun parseMaxLink(url: String): MaxLink? {
    val trimmed = url.trim()
    val withoutScheme =
        trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("max://")
            .removePrefix("www.")
    val hostAndPath = withoutScheme.substringBefore('?').substringBefore('#')
    val host = hostAndPath.substringBefore('/').lowercase()
    if (!isMaxHost(host)) return null
    val path = hostAndPath.substringAfter('/', "").trim('/')
    if (path.isEmpty()) return null
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.first().equals("joincall", ignoreCase = true)) {
        val id = segments.drop(1).firstOrNull() ?: return null
        return MaxLink.JoinCall(id)
    }
    // Everything else is an invite of some kind; the server decides what it points at.
    return MaxLink.Invite(link = "$host/$path", token = segments.last())
}

/** True for max.ru / oneme.ru and their subdomains. */
private fun isMaxHost(host: String): Boolean = MAX_HOSTS.any { host == it || host.endsWith(".$it") }

private val MAX_HOSTS = listOf("max.ru", "oneme.ru")
