package com.avenarius.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.avenarius.app.ui.components.openUriSafely

@Composable
actual fun rememberAppReviewLauncher(): () -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context) {
        {
            // The Play Store app straight on the app's page; the web page if it isn't there.
            // Always the release id, even from a ".dev" debug build.
            val market =
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE"))
                    .setPackage("com.android.vending")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(market)
            } catch (e: ActivityNotFoundException) {
                uriHandler.openUriSafely(AppLinks.PLAY_STORE)
            }
        }
    }
}

private const val PLAY_PACKAGE = "com.avenarius.app"
