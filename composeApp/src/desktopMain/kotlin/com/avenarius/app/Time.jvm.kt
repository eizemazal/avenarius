package com.avenarius.app.ui

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatClock(epochMillis: Long): String =
    java.text
        .SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(epochMillis))

actual fun formatDate(epochMillis: Long): String =
    java.text
        .SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(epochMillis))
