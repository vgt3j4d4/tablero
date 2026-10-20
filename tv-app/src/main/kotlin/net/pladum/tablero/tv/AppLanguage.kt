package net.pladum.tablero.tv

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import java.util.Locale

/** Preferences holding what is chosen in the menu: the language and the layout. */
internal const val SETTINGS_PREFS = "settings"

private const val PREF_LANGUAGE = "language"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * The language chosen in the app's menu (see docs/architecture.md → Localization). Android before 13 has no per-app
 * language, so the choice is kept in preferences and applied to each activity as an override configuration. With
 * nothing chosen the app follows the device language (on Android 13+ also the system per-app setting), and Android's
 * resource fallback shows English for a language that isn't bundled.
 *
 * Only activity contexts carry the choice: resolve user-visible strings from the activity, never from the
 * application context.
 */
object AppLanguage {
    /** The bundled languages, in the order of `res/xml/locales_config.xml`. */
    fun available(context: Context): List<Locale> = context.resources.getXml(R.xml.locales_config).use { parser ->
        buildList {
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let { add(Locale.forLanguageTag(it)) }
                }
            }
        }
    }

    /** The language chosen in the app, or null to follow the device. */
    fun chosen(context: Context): Locale? =
        resolveChosen(prefs(context).getString(PREF_LANGUAGE, null), available(context))

    /** The bundled language [activity] shows, as the menu names it. */
    fun inUse(activity: Activity): Locale =
        bundledMatch(activity.resources.configuration.primaryLocale, available(activity))

    /** Saves [locale] (null = follow the device) and recreates [activity] so it shows the new language. */
    fun set(activity: Activity, locale: Locale?) {
        val edit = prefs(activity).edit()
        if (locale == null) edit.remove(PREF_LANGUAGE) else edit.putString(PREF_LANGUAGE, locale.toLanguageTag())
        edit.apply()
        activity.recreate()
    }

    /**
     * The configuration an activity applies with `applyOverrideConfiguration` in `attachBaseContext`, or null when
     * nothing is chosen. Only the locale is set; every other field stays undefined and keeps the device's value.
     */
    fun overrideConfiguration(base: Context): Configuration? {
        val locale = chosen(base) ?: return null
        return Configuration().apply { setLocale(locale) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}

/** The saved language [tag] if it is still bundled, else null (follow the device). */
internal fun resolveChosen(tag: String?, bundled: List<Locale>): Locale? =
    tag?.let { saved -> bundled.firstOrNull { it.toLanguageTag() == saved } }

/**
 * The bundled language that shows for [locale]: the same one, else the same language in another region (the device
 * may be set to `es-PE`), else the first bundled one, English, which Android's resource fallback shows.
 */
internal fun bundledMatch(locale: Locale, bundled: List<Locale>): Locale =
    bundled.firstOrNull { it == locale } ?: bundled.firstOrNull { it.language == locale.language } ?: bundled.first()

/** A language's name in that language, as the menu lists it: "English", "Español". */
fun Locale.nativeName(): String = getDisplayName(this).replaceFirstChar { it.titlecase(this) }
