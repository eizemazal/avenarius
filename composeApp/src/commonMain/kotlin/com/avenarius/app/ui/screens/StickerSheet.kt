package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerShelf
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.initials
import com.avenarius.app.ui.theme.Radius
import com.avenarius.app.ui.theme.Spacing

/** Key of the «Недавние» tab in the set row (a real set has a numeric id). */
private const val RECENT = -1L

/**
 * The sticker panel: a row of the user's sets («Недавние» first), the selected one's stickers
 * in a grid; a tap sends. A set's stickers are asked for when it is selected, not before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerSheet(
    /** Null while it loads. */
    shelf: StickerShelf?,
    stickers: Map<Long, Sticker>,
    onLoad: (List<Long>) -> Unit,
    onSend: (Sticker) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<Long?>(null) }
    val current = selected ?: if (shelf?.recent?.isNotEmpty() == true) RECENT else shelf?.sets?.firstOrNull()?.id
    val ids =
        if (current == RECENT) {
            shelf?.recent.orEmpty()
        } else {
            shelf
                ?.sets
                ?.firstOrNull { it.id == current }
                ?.stickerIds
                .orEmpty()
        }
    LaunchedEffect(current, ids) { if (ids.isNotEmpty()) onLoad(ids) }
    AppSheet(onDismiss) {
        Column(Modifier.padding(bottom = Spacing.m)) {
            when {
                shelf == null ->
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                shelf.sets.isEmpty() && shelf.recent.isEmpty() ->
                    Text(
                        "У вас пока нет стикеров. Добавить наборы можно в официальном приложении.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
                    )
                else -> {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (shelf.recent.isNotEmpty()) {
                            item(key = "recent") {
                                SetTab(current == RECENT, "Недавние", { selected = RECENT }) {
                                    Icon(
                                        AppIcons.Clock,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                        items(shelf.sets, key = { it.id }) { set ->
                            SetTab(current == set.id, set.name, { selected = set.id }) {
                                if (set.iconUrl != null) {
                                    AsyncImage(model = set.iconUrl, contentDescription = null, modifier = Modifier.size(32.dp))
                                } else {
                                    Text(initials(set.name), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    LazyVerticalGrid(
                        GridCells.Fixed(4),
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(horizontal = Spacing.m)
                            .padding(top = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        items(ids, key = { it }) { id ->
                            val sticker = stickers[id]
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(Radius.small))
                                    .background(if (sticker == null) cs.surfaceContainerHigh else Color.Transparent)
                                    .clickable(enabled = sticker != null) { sticker?.let(onSend) }
                                    .padding(Spacing.xs),
                            ) {
                                if (sticker !=
                                    null
                                ) {
                                    AsyncImage(model = sticker.url, contentDescription = "Стикер", modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetTab(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radius.small))
            .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
