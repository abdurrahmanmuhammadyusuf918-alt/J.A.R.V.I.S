package uz.jarvis.app

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Ruxsatlarni tekshirish va so'rash uchun intent'lar. */
object Perms {

    fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun accessibility(ctx: Context): Boolean {
        val me = ComponentName(ctx, JarvisAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun assistant(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }
        val v = runCatching {
            Settings.Secure.getString(ctx.contentResolver, "voice_interaction_service")
        }.getOrNull().orEmpty()
        return v.startsWith(ctx.packageName + "/")
    }

    fun battery(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun overlayIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batteryIntent(ctx: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    fun assistantIntent(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            }
        }
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }

    fun appDetailsIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
}
