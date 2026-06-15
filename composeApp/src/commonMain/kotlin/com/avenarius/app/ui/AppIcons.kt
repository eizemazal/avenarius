package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.avenarius.app.resources.Res
import com.avenarius.app.resources.arrow_back
import com.avenarius.app.resources.attach_file
import com.avenarius.app.resources.chat
import com.avenarius.app.resources.check
import com.avenarius.app.resources.close
import com.avenarius.app.resources.collapse_content
import com.avenarius.app.resources.content_copy
import com.avenarius.app.resources.done_all
import com.avenarius.app.resources.edit
import com.avenarius.app.resources.expand_content
import com.avenarius.app.resources.forward
import com.avenarius.app.resources.group
import com.avenarius.app.resources.more_vert
import com.avenarius.app.resources.play_arrow
import com.avenarius.app.resources.reply
import com.avenarius.app.resources.search
import com.avenarius.app.resources.send
import com.avenarius.app.resources.settings
import org.jetbrains.compose.resources.painterResource

/**
 * Material Symbols icons, downloaded as SVGs under composeResources/drawable and
 * exposed as [Painter]s for use with `Icon(...)`. Each painter is monochrome, so
 * `Icon` tints it with the current content color (or an explicit `tint`).
 *
 * Replaces the Unicode/emoji glyphs we used to render via `Text(...)`, which
 * relied on the platform fallback font and looked inconsistent across Android
 * and desktop.
 */
object AppIcons {
    val Back: Painter @Composable get() = painterResource(Res.drawable.arrow_back)
    val More: Painter @Composable get() = painterResource(Res.drawable.more_vert)
    val Send: Painter @Composable get() = painterResource(Res.drawable.send)
    val Close: Painter @Composable get() = painterResource(Res.drawable.close)
    val Edit: Painter @Composable get() = painterResource(Res.drawable.edit)
    val Play: Painter @Composable get() = painterResource(Res.drawable.play_arrow)
    val Attach: Painter @Composable get() = painterResource(Res.drawable.attach_file)
    val Search: Painter @Composable get() = painterResource(Res.drawable.search)

    // Message context-menu actions.
    val Reply: Painter @Composable get() = painterResource(Res.drawable.reply)
    val Copy: Painter @Composable get() = painterResource(Res.drawable.content_copy)
    val Forward: Painter @Composable get() = painterResource(Res.drawable.forward)

    // Expand/collapse the extra row of reactions.
    val Expand: Painter @Composable get() = painterResource(Res.drawable.expand_content)
    val Collapse: Painter @Composable get() = painterResource(Res.drawable.collapse_content)

    // Message delivery state: single check = delivered, double check = read.
    val Delivered: Painter @Composable get() = painterResource(Res.drawable.check)
    val Read: Painter @Composable get() = painterResource(Res.drawable.done_all)

    // Bottom navigation tabs.
    val Chats: Painter @Composable get() = painterResource(Res.drawable.chat)
    val Contacts: Painter @Composable get() = painterResource(Res.drawable.group)
    val Settings: Painter @Composable get() = painterResource(Res.drawable.settings)
}
