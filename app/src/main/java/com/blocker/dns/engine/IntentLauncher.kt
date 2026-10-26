package com.blocker.dns.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object IntentLauncher {
    private const val TAG = "IntentLauncher"

    const val PACKAGE_CHROME = "com.android.chrome"
    const val PACKAGE_BRAVE = "com.brave.browser"

    fun launchAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun launchPrivateDnsSettings(context: Context) {
        val intents = listOf(
            Intent("android.net.conn.PRIVATE_DNS"),
            Intent().setClassName("com.android.settings", "com.android.settings.network.PrivateDnsSettingsActivity"),
            Intent("android.settings.NETWORK_PROVIDER_SETTINGS"),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            val target = intent.action ?: intent.component?.className ?: "unknown_intent"
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Launched intent: $target")
                LogExporter.log("[IntentLauncher] Launched intent: $target")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed launching $target: ${e.message}")
                LogExporter.log("[IntentLauncher] Failed launching $target: ${e.message}")
            }
        }
        LogExporter.log("[IntentLauncher] All private DNS intents failed!")
    }

    fun launchWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            LogExporter.log("[IntentLauncher] Launched ACTION_WIFI_SETTINGS")
        } catch (e: Exception) {
            LogExporter.log("[IntentLauncher] Failed ACTION_WIFI_SETTINGS: ${e.message}. Fallback to ACTION_SETTINGS")
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            LogExporter.log("[IntentLauncher] Successfully launched package: $packageName")
            true
        } else {
            Log.w(TAG, "App not installed: $packageName")
            LogExporter.log("[IntentLauncher] App package not found/installed: $packageName")
            false
        }
    }
}
