package net.pladum.tablero.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * The way out to Android TV (menu → Exit). tablero is meant to be the default home app with the stock
 * launcher still installed (docs/stick-setup.md), so HOME always brings the scoreboard back and leaving means
 * starting that launcher by name.
 */
object AndroidTv {
    /** Whether HOME opens tablero: it is the default home app, or the only one left enabled. */
    fun isTableroHome(context: Context): Boolean {
        // With several home apps and no default this resolves to the system's chooser.
        val home = context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return home?.activityInfo?.packageName == context.packageName
    }

    /**
     * Opens another home app, a system one (the stock launcher) first. When there is none, e.g. on a stick whose
     * launcher was disabled, it opens Settings instead, so the way out always leads somewhere.
     */
    fun open(context: Context) {
        // Negative priority marks placeholders such as Settings' FallbackHome, shown only while the device boots.
        val launchers = context.packageManager.queryIntentActivities(homeIntent(), 0)
            .filter { it.priority >= 0 }
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
        val launcher = launchers.firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            ?: launchers.firstOrNull()
        val intent = if (launcher != null) {
            homeIntent().setClassName(launcher.packageName, launcher.name)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Log.i(LOG_TAG, "Exit: ${intent.component ?: intent.action}")
        } catch (e: ActivityNotFoundException) {
            Log.e(LOG_TAG, "Exit: nothing to open", e)
        }
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}
