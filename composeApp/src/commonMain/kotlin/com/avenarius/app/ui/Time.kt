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
