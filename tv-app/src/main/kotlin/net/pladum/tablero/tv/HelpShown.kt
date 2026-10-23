package net.pladum.tablero.tv

import android.content.Context

private const val PREF_HELP_SHOWN = "helpShown"

/**
 * Whether How to use (design 10) was ever shown. Until it was, tablero opens it by itself when it starts with the
 * clock stopped: a first-time scorer can't know that holding BACK opens the menu where it lives.
 */
object HelpShown {
    fun get(context: Context): Boolean = settingsPrefs(context).getBoolean(PREF_HELP_SHOWN, false)

    fun set(context: Context) {
        settingsPrefs(context).edit().putBoolean(PREF_HELP_SHOWN, true).apply()
    }
}
