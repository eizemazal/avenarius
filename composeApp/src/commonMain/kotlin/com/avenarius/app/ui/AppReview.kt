package com.avenarius.app.ui

import androidx.compose.runtime.Composable

/**
 * Returns an action that opens the app's Google Play page so the user can rate it.
 *
 * Deliberately not Play's in-app review sheet: that one is quota-limited and silently
 * shows nothing when the quota is used up (or the app wasn't installed from Play), which
 * is why Google advises against triggering it from a button — the user taps "rate" and
 * nothing happens. The store page always opens.
 */
@Composable
expect fun rememberAppReviewLauncher(): () -> Unit
