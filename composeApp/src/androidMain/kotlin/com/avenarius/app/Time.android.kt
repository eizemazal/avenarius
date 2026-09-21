package com.avenarius.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatClock(epochMillis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

actual fun formatDate(epochMillis: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(epochMillis))

actual fun formatDay(epochMillis: Long): String = formatDayLabel(epochMillis)

/** Shared JVM impl for the floating date chip (Today/Yesterday/localized day). */
internal fun formatDayLabel(epochMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val now = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(
        a: Calendar,
        b: Calendar,
    ) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val ru = Locale("ru")
    return when {
        sameDay(cal, now) -> "Сегодня"
        sameDay(cal, yesterday) -> "Вчера"
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", ru).format(Date(epochMillis))
        else -> SimpleDateFormat("d MMMM yyyy", ru).format(Date(epochMillis))
    }
}
