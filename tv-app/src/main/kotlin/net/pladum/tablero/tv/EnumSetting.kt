package net.pladum.tablero.tv

import android.content.Context

/**
 * A choice from the menu, kept in preferences by its name under [key] so the board comes back the same after a
 * restart or a reboot. [default] until one is chosen.
 */
open class EnumSetting<T : Enum<T>>(
    private val key: String,
    private val entries: List<T>,
    private val default: T,
) {
    fun get(context: Context): T = resolve(prefs(context).getString(key, null))

    fun set(context: Context, value: T) {
        prefs(context).edit().putString(key, value.name).apply()
    }

    /** The saved [name], or [default] when nothing (or something no longer known) was saved. */
    internal fun resolve(name: String?): T = entries.firstOrNull { it.name == name } ?: default

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
