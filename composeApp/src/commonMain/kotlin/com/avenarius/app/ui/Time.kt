package com.avenarius.app.ui

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

/** True when both timestamps fall on the same local calendar day. */
expect fun sameDay(
    a: Long,
    b: Long,
): Boolean
