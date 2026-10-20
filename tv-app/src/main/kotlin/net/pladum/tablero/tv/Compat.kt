package net.pladum.tablero.tv

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

// Platform calls that need a newer API than minSdk 22 (Fire TV Stick 2nd gen, Android 5.1).

/** The color resource [id]; `Context.getColor` needs API 23. */
fun Context.color(id: Int): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    getColor(id)
} else {
    @Suppress("DEPRECATION")
    resources.getColor(id)
}

/** The configuration's first locale; `Configuration.locales` needs API 24. */
val Configuration.primaryLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) locales[0] else @Suppress("DEPRECATION") locale
