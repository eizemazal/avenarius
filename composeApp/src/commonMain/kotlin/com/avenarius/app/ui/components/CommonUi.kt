package com.avenarius.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.ui.theme.rotateHue

@Composable
internal fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private val UrlRegex =
    Regex(
        // E-mails come before bare domains, otherwise "ivan@mail.ru" links only "mail.ru".
        """(https?://[^\s]+)|([\w.+-]+@[\w-]+(?:\.[\w-]+)+)|([\w.-]+\.(?:ru|com|org|net|me|io|info|app|tv|dev)(?:/[^\s]*)?)""",
        RegexOption.IGNORE_CASE,
    )

/** Renders [text] with embedded URLs as tappable links (opens the system browser). */

@Composable
internal fun LinkedText(
    text: String,
    style: TextStyle,
    color: Color,
    /**
     * Given a tapped URL, handles it in-app and returns true — used for Max links,
     * which belong in the app rather than a browser. Anything it declines is opened
     * externally as before.
     */
    onInternalLink: ((String) -> Boolean)? = null,
    /** Must contrast with what the text sits on; an accent-filled bubble needs its own. */
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    // Open links via our safe handler so an unhandled scheme (mailto: with no email
    // app, etc.) can't crash the app.
    val uriHandler = LocalUriHandler.current
    val onLink =
        LinkInteractionListener { link ->
            (link as? LinkAnnotation.Url)?.url?.let { url ->
                if (onInternalLink?.invoke(url) != true) uriHandler.openUriSafely(url)
            }
        }
    val annotated =
        remember(text, linkColor, onLink) {
            buildAnnotatedString {
                var last = 0
                for (m in UrlRegex.findAll(text)) {
                    if (m.range.first > last) append(text.substring(last, m.range.first))
                    val raw = m.value
                    val url =
                        when {
                            raw.startsWith("http", ignoreCase = true) -> raw
                            raw.startsWith("mailto:", ignoreCase = true) -> raw
                            raw.contains("@") && !raw.contains("/") -> "mailto:$raw"
                            else -> "https://$raw"
                        }
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            linkInteractionListener = onLink,
                        ),
                    ) { append(raw) }
                    last = m.range.last + 1
                }
                if (last < text.length) append(text.substring(last))
            }
        }
    Text(annotated, style = style, color = color)
}

/** Opens [url] without crashing if nothing can handle it; bare emails go to mailto:. */
internal fun UriHandler.openUriSafely(url: String) {
    val target =
        when {
            url.contains("://") || url.startsWith("mailto:", true) || url.startsWith("tel:", true) -> url
            url.contains("@") && !url.contains("/") -> "mailto:$url"
            else -> url
        }
    runCatching { openUri(target) }
}

@Composable
internal fun Avatar(
    name: String,
    url: String?,
    size: Dp,
    onClick: (() -> Unit)? = null,
    online: Boolean = false,
) {
    Box(Modifier.size(size)) {
        val (light, dark) = avatarGradient(name)
        var mod = Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(light, dark)))
        if (onClick != null) mod = mod.clickable(onClick = onClick)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = mod)
        } else {
            Box(mod, contentAlignment = Alignment.Center) {
                Text(
                    initials(name),
                    color = MaterialTheme.colorScheme.onPrimary,
                    // Sized from the circle, not the user's font scale, so two letters always fit.
                    fontSize = with(LocalDensity.current) { (size * 0.38f).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        if (online) OnlineDot(size, Modifier.align(Alignment.BottomEnd))
    }
}

/** Accent presence dot, with a ring in the surface colour so it reads against the avatar. */

@Composable
internal fun OnlineDot(
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    val ring = avatarSize * 0.30f
    Box(
        modifier.size(ring).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(ring * 0.7f).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

private const val AVATAR_HUES = 8

/** A stable per-person turn of the accent's hue, so avatars differ yet follow the theme. */
private fun hueTurn(key: String): Float = (key.hashCode() and 0x7fffffff) % AVATAR_HUES * (360f / AVATAR_HUES)

@Composable
private fun avatarGradient(key: String): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val turn = hueTurn(key)
    return lerp(cs.primary, cs.onPrimary, 0.12f).rotateHue(turn) to cs.primary.rotateHue(turn)
}

/** The sender's avatar hue, for their name in group chats (primary is readable on surfaces). */
@Composable
internal fun senderNameColor(key: String): Color = MaterialTheme.colorScheme.primary.rotateHue(hueTurn(key))

/** Up to two initials from the first letters of the first words: "Анна Петрова" -> "АП". */
internal fun initials(name: String): String =
    name
        .split(' ')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }

@Composable
internal fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SmallSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.height(20.dp),
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Small wrapper for a clickable row that works across platforms. */

internal fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
