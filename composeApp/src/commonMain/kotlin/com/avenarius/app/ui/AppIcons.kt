package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.avenarius.app.resources.Res
import com.avenarius.app.resources.add
import com.avenarius.app.resources.alternate_email
import com.avenarius.app.resources.arrow_back
import com.avenarius.app.resources.arrow_downward
import com.avenarius.app.resources.attach_file
import com.avenarius.app.resources.bar_chart
import com.avenarius.app.resources.block
import com.avenarius.app.resources.bookmark_filled
import com.avenarius.app.resources.brightness_auto
import com.avenarius.app.resources.call
import com.avenarius.app.resources.call_end
import com.avenarius.app.resources.cameraswitch
import com.avenarius.app.resources.campaign_filled
import com.avenarius.app.resources.chat
import com.avenarius.app.resources.chat_filled
import com.avenarius.app.resources.check
import com.avenarius.app.resources.check_circle_filled
import com.avenarius.app.resources.chevron_right
import com.avenarius.app.resources.cleaning_services
import com.avenarius.app.resources.close
import com.avenarius.app.resources.collapse_content
import com.avenarius.app.resources.content_copy
import com.avenarius.app.resources.dark_mode
import com.avenarius.app.resources.database
import com.avenarius.app.resources.delete
import com.avenarius.app.resources.description_filled
import com.avenarius.app.resources.devices_filled
import com.avenarius.app.resources.done_all
import com.avenarius.app.resources.download
import com.avenarius.app.resources.edit
import com.avenarius.app.resources.expand_content
import com.avenarius.app.resources.favorite_filled
import com.avenarius.app.resources.folder
import com.avenarius.app.resources.format_size
import com.avenarius.app.resources.forward
import com.avenarius.app.resources.group
import com.avenarius.app.resources.group_add
import com.avenarius.app.resources.group_filled
import com.avenarius.app.resources.image_filled
import com.avenarius.app.resources.info
import com.avenarius.app.resources.keep_off
import com.avenarius.app.resources.keyboard_arrow_down
import com.avenarius.app.resources.keyboard_arrow_up
import com.avenarius.app.resources.keyboard_return
import com.avenarius.app.resources.light_mode
import com.avenarius.app.resources.link
import com.avenarius.app.resources.location_on
import com.avenarius.app.resources.lock
import com.avenarius.app.resources.logout
import com.avenarius.app.resources.mark_chat_read
import com.avenarius.app.resources.mark_chat_unread
import com.avenarius.app.resources.mic
import com.avenarius.app.resources.mic_off
import com.avenarius.app.resources.mood
import com.avenarius.app.resources.more_vert
import com.avenarius.app.resources.notifications
import com.avenarius.app.resources.notifications_off
import com.avenarius.app.resources.open_in_new
import com.avenarius.app.resources.palette_filled
import com.avenarius.app.resources.pause
import com.avenarius.app.resources.person_add
import com.avenarius.app.resources.person_filled
import com.avenarius.app.resources.photo_camera_filled
import com.avenarius.app.resources.play_arrow
import com.avenarius.app.resources.push_pin
import com.avenarius.app.resources.qr_code_scanner
import com.avenarius.app.resources.refresh
import com.avenarius.app.resources.reply
import com.avenarius.app.resources.schedule
import com.avenarius.app.resources.search
import com.avenarius.app.resources.send
import com.avenarius.app.resources.settings
import com.avenarius.app.resources.settings_filled
import com.avenarius.app.resources.share
import com.avenarius.app.resources.shield_filled
import com.avenarius.app.resources.smart_toy_filled
import com.avenarius.app.resources.verified_filled
import com.avenarius.app.resources.video_chat
import com.avenarius.app.resources.videocam
import com.avenarius.app.resources.videocam_off
import com.avenarius.app.resources.visibility
import com.avenarius.app.resources.visibility_off
import com.avenarius.app.resources.zoom_in
import org.jetbrains.compose.resources.painterResource

/**
 * Material Symbols (Rounded) icons, downloaded as SVGs under composeResources/drawable and
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

    // Filled variants for the selected tab, per Material 3 navigation-bar guidance.
    val ChatsSelected: Painter @Composable get() = painterResource(Res.drawable.chat_filled)
    val ContactsSelected: Painter @Composable get() = painterResource(Res.drawable.group_filled)
    val SettingsSelected: Painter @Composable get() = painterResource(Res.drawable.settings_filled)

    /** Muted chat marker in the chat list. */
    val Muted: Painter @Composable get() = painterResource(Res.drawable.notifications_off)
    val Unmuted: Painter @Composable get() = painterResource(Res.drawable.notifications)
    val Pin: Painter @Composable get() = painterResource(Res.drawable.push_pin)

    // Settings rows (filled, drawn white on a colored tile).
    val QrScan: Painter @Composable get() = painterResource(Res.drawable.qr_code_scanner)
    val Lock: Painter @Composable get() = painterResource(Res.drawable.lock)
    val Clean: Painter @Composable get() = painterResource(Res.drawable.cleaning_services)
    val Info: Painter @Composable get() = painterResource(Res.drawable.info)
    val Logout: Painter @Composable get() = painterResource(Res.drawable.logout)
    val HideOnline: Painter @Composable get() = painterResource(Res.drawable.visibility_off)

    // Calls: chat-header buttons and in-call controls.
    // Jump-to-latest button in the chat.
    val ScrollDown: Painter @Composable get() = painterResource(Res.drawable.arrow_downward)
    val Call: Painter @Composable get() = painterResource(Res.drawable.call)
    val VideoCall: Painter @Composable get() = painterResource(Res.drawable.video_chat)
    val CallEnd: Painter @Composable get() = painterResource(Res.drawable.call_end)
    val MicOff: Painter @Composable get() = painterResource(Res.drawable.mic_off)
    val Video: Painter @Composable get() = painterResource(Res.drawable.videocam)
    val VideoOff: Painter @Composable get() = painterResource(Res.drawable.videocam_off)
    val SwitchCamera: Painter @Composable get() = painterResource(Res.drawable.cameraswitch)

    // Redesign additions.
    val Add: Painter @Composable get() = painterResource(Res.drawable.add)
    val PersonAdd: Painter @Composable get() = painterResource(Res.drawable.person_add)
    val GroupAdd: Painter @Composable get() = painterResource(Res.drawable.group_add)
    val Link: Painter @Composable get() = painterResource(Res.drawable.link)
    val Share: Painter @Composable get() = painterResource(Res.drawable.share)
    val ArrowDown: Painter @Composable get() = painterResource(Res.drawable.keyboard_arrow_down)
    val ArrowUp: Painter @Composable get() = painterResource(Res.drawable.keyboard_arrow_up)
    val Emoji: Painter @Composable get() = painterResource(Res.drawable.mood)
    val MarkRead: Painter @Composable get() = painterResource(Res.drawable.mark_chat_read)
    val MarkUnread: Painter @Composable get() = painterResource(Res.drawable.mark_chat_unread)
    val Location: Painter @Composable get() = painterResource(Res.drawable.location_on)
    val Poll: Painter @Composable get() = painterResource(Res.drawable.bar_chart)
    val Block: Painter @Composable get() = painterResource(Res.drawable.block)
    val Devices: Painter @Composable get() = painterResource(Res.drawable.devices_filled)
    val Palette: Painter @Composable get() = painterResource(Res.drawable.palette_filled)
    val Shield: Painter @Composable get() = painterResource(Res.drawable.shield_filled)
    val Channel: Painter @Composable get() = painterResource(Res.drawable.campaign_filled)
    val Person: Painter @Composable get() = painterResource(Res.drawable.person_filled)
    val Username: Painter @Composable get() = painterResource(Res.drawable.alternate_email)
    val Clock: Painter @Composable get() = painterResource(Res.drawable.schedule)
    val Unpin: Painter @Composable get() = painterResource(Res.drawable.keep_off)
    val Download: Painter @Composable get() = painterResource(Res.drawable.download)
    val DarkMode: Painter @Composable get() = painterResource(Res.drawable.dark_mode)
    val LightMode: Painter @Composable get() = painterResource(Res.drawable.light_mode)
    val AutoMode: Painter @Composable get() = painterResource(Res.drawable.brightness_auto)
    val ChevronRight: Painter @Composable get() = painterResource(Res.drawable.chevron_right)
    val Visibility: Painter @Composable get() = painterResource(Res.drawable.visibility)
    val Bookmark: Painter @Composable get() = painterResource(Res.drawable.bookmark_filled)
    val Image: Painter @Composable get() = painterResource(Res.drawable.image_filled)
    val Camera: Painter @Composable get() = painterResource(Res.drawable.photo_camera_filled)
    val Document: Painter @Composable get() = painterResource(Res.drawable.description_filled)
    val Folder: Painter @Composable get() = painterResource(Res.drawable.folder)
    val Storage: Painter @Composable get() = painterResource(Res.drawable.database)
    val FontSize: Painter @Composable get() = painterResource(Res.drawable.format_size)
    val Zoom: Painter @Composable get() = painterResource(Res.drawable.zoom_in)
    val Verified: Painter @Composable get() = painterResource(Res.drawable.verified_filled)
    val CheckCircle: Painter @Composable get() = painterResource(Res.drawable.check_circle_filled)
    val Enter: Painter @Composable get() = painterResource(Res.drawable.keyboard_return)
    val Heart: Painter @Composable get() = painterResource(Res.drawable.favorite_filled)
    val Bot: Painter @Composable get() = painterResource(Res.drawable.smart_toy_filled)
}
