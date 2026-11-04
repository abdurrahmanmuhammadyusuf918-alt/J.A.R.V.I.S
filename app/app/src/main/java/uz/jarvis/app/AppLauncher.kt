package uz.jarvis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/** Telefondagi ilovalarni topish va ovozli buyruqdan ochish. */
object AppLauncher {

    data class AppInfo(val label: String, val pkg: String)

    private val openVerbs = listOf(
        "ochib bering", "ochib ber", "ishga tushiring", "ishga tushir",
        "oching", "ochgin", "och", "yoqing", "yoq", "open", "launch", "запусти", "открой"
    ).sortedByDescending { it.length }

    private val openPrefixes = listOf("open ", "launch ", "открой ", "запусти ")

    // Tez-tez so'raladigan nomlar uchun sinonimlar
    private val aliases = mapOf(
        "sozlamalar" to "settings", "sozlama" to "settings", "nastroyki" to "settings",
        "kamera" to "camera", "galereya" to "gallery", "rasmlar" to "photos",
        "kalkulyator" to "calculator", "soat" to "clock", "xarita" to "maps",
        "ютуб" to "youtube", "телеграм" to "telegram", "инстаграм" to "instagram",
        "vatsap" to "whatsapp", "watsap" to "whatsapp", "yutub" to "youtube"
    )

    private fun norm(s: String) =
        s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Matndan ilova nomini ajratadi. Buyruq bo'lmasa null qaytaradi. */
    fun parseOpenCommand(raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT).trimEnd('.', '!', '?')
        if (text.isEmpty()) return null

        for (p in openPrefixes) {
            if (text.startsWith(p)) return text.removePrefix(p).trim().ifEmpty { null }
        }
        for (v in openVerbs) {
            if (text == v) return null
            if (text.endsWith(" $v")) {
                return text.removeSuffix(v).trim().ifEmpty { null }
            }
        }
        return null
    }

    fun installedApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private fun candidates(name: String): List<String> {
        val base = name.trim()
        val stripped = base.removeSuffix("ni").removeSuffix("ни").trim()
        return listOf(base, stripped).distinct().filter { it.length >= 2 }.map {
            norm(aliases[it] ?: it)
        }
    }

    fun find(ctx: Context, name: String): AppInfo? {
        val apps = installedApps(ctx).map { it to norm(it.label) }
        for (q in candidates(name)) {
            apps.firstOrNull { it.second == q }?.let { return it.first }
            apps.firstOrNull { it.second.startsWith(q) }?.let { return it.first }
            apps.firstOrNull { it.second.contains(q) }?.let { return it.first }
            if (q.length >= 4) {
                apps.firstOrNull { it.second.length >= 3 && q.contains(it.second) }?.let { return it.first }
            }
        }
        return null
    }

    /** Ilovani ochadi. Muvaffaqiyatli bo'lsa ilova nomini qaytaradi. */
    fun open(ctx: Context, name: String): String? {
        val app = find(ctx, name) ?: return null
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(launch)
            app.label
        } catch (e: Exception) {
            null
        }
    }
}
