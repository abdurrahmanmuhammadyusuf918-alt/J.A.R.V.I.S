package uz.jarvis.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper

/**
 * Boshqa ilovalar ustida panel va holat chizig'ini ko'rsatadi.
 * Accessibility yoqilgan bo'lsa u orqali, aks holda "Boshqa ilovalar ustida ko'rsatish" ruxsati bilan.
 */
object OverlayController {

    private val main = Handler(Looper.getMainLooper())

    private class Target(val ctx: Context, val type: Int)

    private fun target(ctx: Context): Target? {
        val svc = JarvisAccessibilityService.instance
        if (svc != null) return Target(svc, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        val app = ctx.applicationContext
        if (Settings.canDrawOverlays(app)) {
            return Target(app, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        return null
    }

    fun canShow(ctx: Context) = target(ctx) != null

    // ---- Panel ----

    private var panel: AssistantPanel? = null
    private var panelWm: WindowManager? = null

    /** Panelni ko'rsatadi va tinglashni boshlaydi. Ko'rsatib bo'lmasa false. Faqat asosiy oqimda. */
    fun showPanel(ctx: Context): Boolean {
        if (panel != null) return true
        val t = target(ctx) ?: return false
        val wm = t.ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val p = AssistantPanel(t.ctx) { hidePanel() }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            t.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        try {
            wm.addView(p.view, lp)
        } catch (e: Exception) {
            return false
        }
        panel = p
        panelWm = wm
        p.start()
        return true
    }

    fun hidePanel() {
        val p = panel ?: return
        panel = null
        p.stop()
        try { panelWm?.removeView(p.view) } catch (_: Exception) {}
        panelWm = null
    }

    // ---- Holat chizig'i (agent ishlayotganda) ----

    private var statusRoot: View? = null
    private var statusText: TextView? = null
    private var statusStop: TextView? = null
    private var statusWm: WindowManager? = null
    private val hideStatusRunnable = Runnable { removeStatus() }

    fun showStatus(ctx: Context, text: String, onStop: (() -> Unit)?) {
        main.removeCallbacks(hideStatusRunnable)
        val t = target(ctx)
        if (t == null) {
            Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_SHORT).show()
            return
        }
        if (statusRoot == null) {
            val c = t.ctx
            val d = c.resources.displayMetrics.density
            val txt = TextView(c).apply { setTextColor(Color.WHITE); textSize = 14f }
            val stop = TextView(c).apply {
                setTextColor(Color.parseColor("#FF4FA3")); textSize = 14f; text = "  ✕ To'xtat"
            }
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2101420"))
                    cornerRadius = 24 * d
                    setStroke((1 * d).toInt(), Color.parseColor("#3D8BFF"))
                }
                addView(txt)
                addView(stop)
            }
            val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                t.type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (48 * d).toInt()
            }
            try {
                wm.addView(row, lp)
            } catch (e: Exception) {
                Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_SHORT).show()
                return
            }
            statusRoot = row; statusText = txt; statusStop = stop; statusWm = wm
        }
        statusText?.text = text
        statusStop?.visibility = if (onStop != null) View.VISIBLE else View.GONE
        statusStop?.setOnClickListener { onStop?.invoke() }
    }

    fun hideStatus(delayMs: Long) {
        main.removeCallbacks(hideStatusRunnable)
        main.postDelayed(hideStatusRunnable, delayMs)
    }

    private fun removeStatus() {
        val r = statusRoot ?: return
        try { statusWm?.removeView(r) } catch (_: Exception) {}
        statusRoot = null; statusText = null; statusStop = null; statusWm = null
    }
}
