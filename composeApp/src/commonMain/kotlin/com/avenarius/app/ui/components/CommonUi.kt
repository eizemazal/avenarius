package com.avenarius.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.TextSpan
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.theme.Radius
import com.avenarius.app.ui.theme.Spacing
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

/**
 * A modal sheet that opens fully expanded. A sheet whose content shrinks under it (a list emptied by its own
 * button) can settle hidden without asking to be dismissed, leaving its invisible window over the screen to eat
 * every touch: once it has shown and is hidden again, it is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state) {
        snapshotFlow { state.isVisible }.dropWhile { !it }.first { !it }
        dismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        // The stock handle is described in the system language («Drag handle»); the app speaks Russian.
        // The scrim's «Close sheet» has no parameter in this Material version.
        dragHandle = {
            Box(Modifier.clearAndSetSemantics { contentDescription = "Потяните вниз, чтобы закрыть" }) { BottomSheetDefaults.DragHandle() }
        },
        content = content,
    )
}

/**
 * A horizontally scrolling row whose end fades out while there is more to scroll to, so the item cut by the
 * edge reads as "more this way" rather than a clipped label.
 */
internal fun Modifier.fadingEndScroll(state: ScrollState): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (state.canScrollForward) {
                val fade = 40.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                    topLeft = Offset(size.width - fade, 0f),
                    blendMode = BlendMode.DstIn,
                )
            }
        }.horizontalScroll(state)

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

/**
 * Renders [text] with embedded URLs as tappable links (opens the system browser),
 * the server's formatting [spans] applied, and occurrences of [highlight] marked.
 */
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
    spans: List<TextSpan> = emptyList(),
    highlight: String? = null,
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
        remember(text, linkColor, onLink, spans, highlight) {
            val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
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
                    withLink(LinkAnnotation.Url(url, linkStyles, linkInteractionListener = onLink)) { append(raw) }
                    last = m.range.last + 1
                }
                if (last < text.length) append(text.substring(last))
                // The pieces above reproduce [text] exactly, so the server's offsets apply as they are.
                for (span in spans) {
                    val start = span.from.coerceIn(0, text.length)
                    val end = (span.from + span.length).coerceIn(start, text.length)
                    if (start == end) continue
                    spanStyleOf(span.type)?.let { addStyle(it, start, end) }
                    when (span.type) {
                        "LINK" ->
                            span.url?.let {
                                addLink(
                                    LinkAnnotation.Url(it, linkStyles, linkInteractionListener = onLink),
                                    start,
                                    end,
                                )
                            }
                    }
                }
                if (!highlight.isNullOrBlank()) {
                    var at = text.indexOf(highlight, ignoreCase = true)
                    while (at >= 0) {
                        // A highlighter's amber with dark ink: reads on accent and neutral bubbles alike.
                        addStyle(SpanStyle(background = Color(0xFFFFD54F), color = Color(0xFF1A1A1A)), at, at + highlight.length)
                        at = text.indexOf(highlight, at + highlight.length, ignoreCase = true)
                    }
                }
            }
        }
    Text(annotated, style = style, color = color)
}

/** How a formatting run of [type] is drawn; null for runs with no look of their own (links, mentions). */
internal fun spanStyleOf(type: String): SpanStyle? =
    when (type) {
        "STRONG", "HEADING" -> SpanStyle(fontWeight = FontWeight.Bold)
        "EMPHASIZED", "QUOTE" -> SpanStyle(fontStyle = FontStyle.Italic)
        "UNDERLINE" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "STRIKETHROUGH" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "MONOSPACED", "CODE" -> SpanStyle(fontFamily = FontFamily.Monospace)
        else -> null
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
        var mod = Modifier.size(size).clip(CircleShape).background(avatarColor(name))
        if (onClick != null) mod = mod.clickable(onClick = onClick)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = mod)
        } else {
            Box(mod, contentAlignment = Alignment.Center) {
                Text(
                    initials(name),
                    color = Color.White,
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

/** Flat avatar fills: saturated but muted enough for white initials, independent of the accent. */
private val AvatarColors =
    listOf(
        Color(0xFFD9655B), // red
        Color(0xFFE08E45), // orange
        Color(0xFF5FAE57), // green
        Color(0xFF4A90D9), // blue
        Color(0xFF8E6FD8), // violet
        Color(0xFF3BA8B5), // cyan
        Color(0xFFD9628F), // pink
    )

/** A stable per-person pick from [AvatarColors]. */
internal fun avatarColor(key: String): Color = AvatarColors[(key.trim().hashCode() and 0x7fffffff) % AvatarColors.size]

/** The sender's name color in group chats: the same hue as their avatar. */
@Composable
internal fun senderNameColor(key: String): Color = avatarColor(key)

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

/** A round tonal icon button: the app's standard header / toolbar action. */
@Composable
internal fun CircleIconButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A pill-shaped single-line text field (search boxes, sheet inputs). */
@Composable
internal fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: Painter? = AppIcons.Search,
    capitalize: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.m))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
            modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                AppIcons.Close,
                contentDescription = "Очистить",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/**
 * Tile color for a row glyph: a saturated hue [turn] degrees from sky blue, fixed whatever the
 * accent, so settings icons read as a colorful set.
 */
internal fun tileColor(turn: Float): Color = Color.hsv(((207f + turn) % 360f + 360f) % 360f, 0.7f, 0.85f)

private val GROUP_TILE = 32.dp

/** Where row text starts in a [GroupCard]: gutter, icon tile, gap. Dividers start here too. */
internal val GroupKeyline = Spacing.l + GROUP_TILE + Spacing.l
private val DividerWidth = 1.dp

/** True inside a [GroupCard]: its rows then draw dividers between each other. */
private val LocalInGroupCard = staticCompositionLocalOf { false }

/**
 * A rounded card of rows with an optional [title] inside it at the top, in the accent color
 * (settings, profile pages). Rows using [groupRowDivider] (every [GroupRow] does) get a thin
 * divider between consecutive rows: each row draws a line on its top edge and the card hides
 * the one above its first row.
 */
@Composable
internal fun GroupCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    Surface(modifier, shape = RoundedCornerShape(Radius.medium), color = container) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.xs),
                )
            }
            CompositionLocalProvider(LocalInGroupCard provides true) {
                Column(
                    Modifier.fillMaxWidth().drawWithContent {
                        drawContent()
                        // The first row has nothing above it to divide from.
                        drawRect(container, size = Size(size.width, DividerWidth.toPx()))
                    },
                    content = content,
                )
            }
        }
    }
}

/**
 * The divider on top of a row inside a [GroupCard], starting at [start] (the text keyline);
 * nothing outside a card. For custom rows that should be divided like [GroupRow]s.
 */
@Composable
internal fun Modifier.groupRowDivider(start: Dp = GroupKeyline): Modifier {
    if (!LocalInGroupCard.current) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        drawRect(color, topLeft = Offset(start.toPx(), 0f), size = Size(size.width - start.toPx(), DividerWidth.toPx()))
    }
}

/** One tappable row in a [GroupCard]: glyph on a colored tile, title, optional subtitle and trailing part. */
@Composable
internal fun GroupRow(
    icon: Painter,
    tile: Color,
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    /** Non-null renders a trailing switch; the whole row toggles it via [onClick]. */
    checked: Boolean? = null,
    /** A short value shown at the trailing edge (e.g. the current choice). */
    value: String? = null,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .groupRowDivider()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(GROUP_TILE).clip(RoundedCornerShape(Radius.small)).background(if (danger) cs.error else tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) cs.onError else Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) cs.error else cs.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (value != null) {
            Spacer(Modifier.width(Spacing.s))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = cs.primary, maxLines = 1)
        }
        if (checked != null) {
            Spacer(Modifier.width(Spacing.m))
            Switch(checked = checked, onCheckedChange = null)
        }
        if (chevron) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** A square tonal action with a glyph over a label: the row of shortcuts under a profile header. */
@Composable
internal fun QuickAction(
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.medium))
            .background(cs.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** One action row of a bottom sheet. */
@Composable
internal fun SheetAction(
    icon: Painter,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = if (danger) cs.error else cs.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) cs.error else cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
