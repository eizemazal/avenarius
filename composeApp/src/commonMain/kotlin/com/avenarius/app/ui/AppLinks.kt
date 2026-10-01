package com.avenarius.app.ui

/** External pages the app links to. */
internal object AppLinks {
    const val GITHUB = "https://github.com/eizemazal/avenarius"
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.avenarius.app"

    /** Fallback when the server's session-init carried no `recovery-url`. */
    const val MAX_RECOVERY_DEFAULT = "https://go.max.ru/recovery"

    /** The sign-in troubleshooting guide in the repo (docs/login.md). */
    private const val LOGIN_HELP = "$GITHUB/blob/main/docs/login.md"

    /** A section of the sign-in guide; [anchor] is one of the ids defined in docs/login.md. */
    fun loginHelp(anchor: String? = null): String = if (anchor == null) LOGIN_HELP else "$LOGIN_HELP#$anchor"
}
