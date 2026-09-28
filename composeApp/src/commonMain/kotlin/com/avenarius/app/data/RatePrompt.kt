package com.avenarius.app.data

/**
 * When to offer the "rate the app" card: only to people who have really been using the
 * app for a while, never right after something went wrong, and never again once they
 * said no (or went to rate). Stored per install, so it survives logout.
 *
 * This class decides *whether* the user qualifies; *when* on screen to show it (a calm
 * moment on the chat list) is the ViewModel's call.
 *
 * Note Google Play policy: the offer is the same for everyone who qualifies — it never
 * asks whether they like the app first, and never asks for a particular rating.
 */
class RatePrompt(
    private val storage: AppStorage,
) {
    /** Call on every app start / successful sync; counts distinct days of use. */
    fun recordUse(nowMs: Long) {
        if (long(KEY_FIRST_SEEN) == 0L) put(KEY_FIRST_SEEN, nowMs)
        val day = nowMs / DAY_MS
        if (long(KEY_LAST_ACTIVE_DAY) != day) {
            put(KEY_LAST_ACTIVE_DAY, day)
            put(KEY_ACTIVE_DAYS, long(KEY_ACTIVE_DAYS) + 1)
        }
    }

    fun recordMessageSent() = put(KEY_SENT, long(KEY_SENT) + 1)

    /** A crash, a failed send — anything that makes this a bad moment to ask. */
    fun recordProblem(nowMs: Long) = put(KEY_LAST_PROBLEM, nowMs)

    /** "Позже": hide for a while; the second time, for good. */
    fun later(nowMs: Long) {
        val count = long(KEY_LATER_COUNT) + 1
        put(KEY_LATER_COUNT, count)
        if (count >= MAX_LATER) {
            storage.putString(KEY_STATUS, STATUS_NEVER)
        } else {
            put(KEY_LATER_UNTIL, nowMs + LATER_MS)
        }
    }

    /** "Не показывать". */
    fun never() = storage.putString(KEY_STATUS, STATUS_NEVER)

    /** The user went to rate: don't ask again (we can't know whether they did). */
    fun rated() = storage.putString(KEY_STATUS, STATUS_RATED)

    fun isEligible(nowMs: Long): Boolean {
        if (storage.getString(KEY_STATUS) != null) return false
        val firstSeen = long(KEY_FIRST_SEEN)
        return firstSeen > 0 &&
            nowMs - firstSeen >= MIN_INSTALLED_MS &&
            long(KEY_ACTIVE_DAYS) >= MIN_ACTIVE_DAYS &&
            long(KEY_SENT) >= MIN_SENT &&
            nowMs >= long(KEY_LATER_UNTIL) &&
            nowMs - long(KEY_LAST_PROBLEM) >= PROBLEM_QUIET_MS
    }

    private fun long(key: String): Long = storage.getString(key)?.toLongOrNull() ?: 0L

    private fun put(
        key: String,
        value: Long,
    ) = storage.putString(key, value.toString())

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        const val MIN_INSTALLED_MS = 14 * DAY_MS
        const val MIN_ACTIVE_DAYS = 7L
        const val MIN_SENT = 20L
        const val LATER_MS = 30 * DAY_MS
        const val PROBLEM_QUIET_MS = 3 * DAY_MS
        private const val MAX_LATER = 2L

        private const val KEY_FIRST_SEEN = "rate_first_seen"
        private const val KEY_ACTIVE_DAYS = "rate_active_days"
        private const val KEY_LAST_ACTIVE_DAY = "rate_last_active_day"
        private const val KEY_SENT = "rate_sent"
        private const val KEY_LATER_COUNT = "rate_later_count"
        private const val KEY_LATER_UNTIL = "rate_later_until"
        private const val KEY_STATUS = "rate_status"
        private const val STATUS_NEVER = "never"
        private const val STATUS_RATED = "rated"

        /** Public so the Android crash handler can write it synchronously before the process dies. */
        const val KEY_LAST_PROBLEM = "rate_last_problem"
    }
}
