package uz.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/** Yon tugma uzoq bosilganda ekran ustida chiqadigan Jarvis paneli. Ilova ochilmaydi. */
class JarvisSession(ctx: Context) : VoiceInteractionSession(ctx) {

    private var panel: AssistantPanel? = null

    override fun onCreateContentView(): View {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Panel tashqarisiga bosilsa yopiladi
            setOnClickListener { hide() }
        }
        val p = AssistantPanel(context) { hide() }
        panel = p
        root.addView(p.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
            insets
        }
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // Ruxsat faqat ilova ichida so'raladi
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_AUTO_LISTEN, true)
            })
            hide()
            return
        }
        panel?.start()
    }

    override fun onHide() {
        super.onHide()
        panel?.stop()
    }
}
