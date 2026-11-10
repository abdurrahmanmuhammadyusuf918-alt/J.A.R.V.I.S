package uz.jarvis.app

import android.content.Context

object Prefs {
    // Ovoz tanish va o'qish tili: "uz-UZ", "ru-RU", "en-US"
    const val LANGUAGE_TAG = "uz-UZ"

    private fun sp(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun wakeEnabled(c: Context) = sp(c).getBoolean("wake", true)
    fun setWakeEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("wake", v).apply()

    fun setupDone(c: Context) = sp(c).getBoolean("setup_done", false)
    fun setSetupDone(c: Context, v: Boolean) = sp(c).edit().putBoolean("setup_done", v).apply()
}
