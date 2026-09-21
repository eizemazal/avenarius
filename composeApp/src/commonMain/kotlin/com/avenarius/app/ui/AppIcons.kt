package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.avenarius.app.resources.Res
import com.avenarius.app.resources.arrow_back
import com.avenarius.app.resources.attach_file
import com.avenarius.app.resources.call
import com.avenarius.app.resources.call_end
import com.avenarius.app.resources.cameraswitch
import com.avenarius.app.resources.chat
import com.avenarius.app.resources.check
import com.avenarius.app.resources.close
import com.avenarius.app.resources.collapse_content
import com.avenarius.app.resources.content_copy
import com.avenarius.app.resources.delete
import com.avenarius.app.resources.done_all
import com.avenarius.app.resources.edit
import com.avenarius.app.resources.expand_content
import com.avenarius.app.resources.forward
import com.avenarius.app.resources.group
import com.avenarius.app.resources.mic
import com.avenarius.app.resources.mic_off
import com.avenarius.app.resources.more_vert
import com.avenarius.app.resources.open_in_new
import com.avenarius.app.resources.pause
import com.avenarius.app.resources.play_arrow
import com.avenarius.app.resources.refresh
import com.avenarius.app.resources.reply
import com.avenarius.app.resources.search
import com.avenarius.app.resources.send
import com.avenarius.app.resources.settings
import com.avenarius.app.resources.video_chat
import com.avenarius.app.resources.videocam
import com.avenarius.app.resources.videocam_off
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

    /** Re-send a message whose attachments failed to upload. */
    val Retry: Painter @Composable get() = painterResource(Res.drawable.refresh)
    val Close: Painter @Composable get() = painterResource(Res.drawable.close)
    val Edit: Painter @Composable get() = painterResource(Res.drawable.edit)
    val Play: Painter @Composable get() = painterResource(Res.drawable.play_arrow)

    /** Stop playing a voice message. */
    val Pause: Painter @Composable get() = painterResource(Res.drawable.pause)
    val Attach: Painter @Composable get() = painterResource(Res.drawable.attach_file)

    /** Record a voice message. */
    val Mic: Painter @Composable get() = painterResource(Res.drawable.mic)

    /** Record a round video message. */
    val VideoNote: Painter @Composable get() = painterResource(Res.drawable.video_chat)

    /** Open an already-downloaded file in another app. */
    val Open: Painter @Composable get() = painterResource(Res.drawable.open_in_new)
    val Search: Painter @Composable get() = painterResource(Res.drawable.search)

    // Message context-menu actions.
    val Reply: Painter @Composable get() = painterResource(Res.drawable.reply)
    val Copy: Painter @Composable get() = painterResource(Res.drawable.content_copy)
    val Forward: Painter @Composable get() = painterResource(Res.drawable.forward)
    val Delete: Painter @Composable get() = painterResource(Res.drawable.delete)

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

    // Calls: chat-header buttons and in-call controls.
    val Call: Painter @Composable get() = painterResource(Res.drawable.call)
    val VideoCall: Painter @Composable get() = painterResource(Res.drawable.video_chat)
    val CallEnd: Painter @Composable get() = painterResource(Res.drawable.call_end)
    val MicOff: Painter @Composable get() = painterResource(Res.drawable.mic_off)
    val Video: Painter @Composable get() = painterResource(Res.drawable.videocam)
    val VideoOff: Painter @Composable get() = painterResource(Res.drawable.videocam_off)
    val SwitchCamera: Painter @Composable get() = painterResource(Res.drawable.cameraswitch)
}
