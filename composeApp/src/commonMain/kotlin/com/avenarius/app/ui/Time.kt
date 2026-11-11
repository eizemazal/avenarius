package com.avenarius.app.ui

import com.avenarius.app.net.Presence

/** Current wall-clock time in milliseconds. Implemented per platform. */
expect fun nowMillis(): Long

/** Formats an epoch-millis timestamp as a local "HH:mm" clock string. */
expect fun formatClock(epochMillis: Long): String

/** Formats an epoch-millis timestamp as a local "dd.MM.yyyy" date string. */
expect fun formatDate(epochMillis: Long): String

/**
 * A friendly day label for the floating date chip: "Сегодня"/"Вчера" for today and
 * yesterday, otherwise a localized day+month (with the year only when it differs from now).
 */
expect fun formatDay(epochMillis: Long): String

/**
 * Compact timestamp for a chat-list row, as the official client shows it: "HH:mm"
 * today, "Вчера" yesterday, "dd.MM" within the year, "dd.MM.yy" otherwise.
 */
expect fun formatListTime(epochMillis: Long): String

/** Telegram-style last-seen line for a chat header; null when nothing is known. */
fun lastSeenText(
    p: Presence,
    nowMs: Long,
): String? {
    val seenMs = (p.seen ?: 0L) * 1000
    return when {
        p.online -> "в сети"
        p.status == 2 -> "был(а) недавно"
        p.status == 3 -> "был(а) давно"
        seenMs <= 0 -> null
        sameDay(seenMs, nowMs) -> "был(а) в сети в ${formatClock(seenMs)}"
        sameDay(seenMs, nowMs - 86_400_000) -> "был(а) в сети вчера в ${formatClock(seenMs)}"
        else -> "был(а) в сети ${formatDate(seenMs)}"
    }
}

/** True when both timestamps fall on the same local calendar day. */
expect fun sameDay(
    a: Long,
    b: Long,
): Boolean
