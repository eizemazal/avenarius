package com.avenarius.app.ui

/** Current wall-clock time in milliseconds. Implemented per platform. */
expect fun nowMillis(): Long

/** Formats an epoch-millis timestamp as a local "HH:mm" clock string. */
expect fun formatClock(epochMillis: Long): String

/** Formats an epoch-millis timestamp as a local "dd.MM.yyyy" date string. */
expect fun formatDate(epochMillis: Long): String
