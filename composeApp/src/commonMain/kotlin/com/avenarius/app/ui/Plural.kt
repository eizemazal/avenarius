package com.avenarius.app.ui

/** The Russian form of a counted noun for [n]: [one] (1, 21), [few] (2-4, 22) or [many] (5-20, 11-14, 0). */
internal fun plural(
    n: Int,
    one: String,
    few: String,
    many: String,
): String {
    val tens = n % 100
    val units = n % 10
    return when {
        tens in 11..14 -> many
        units == 1 -> one
        units in 2..4 -> few
        else -> many
    }
}
