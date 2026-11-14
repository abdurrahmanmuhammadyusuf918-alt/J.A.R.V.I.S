package uz.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Minimalist "Gemini" uslubidagi yordamchi paneli. Yon tugma (VoiceInteractionSession)
 * va "Jarvis" deb chaqirish (overlay) uchun umumiy.
 */
class AssistantPanel(private val ctx: Context, private val onClose: () -> Unit) {

    private val appCtx: Context = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val api = JarvisApi()
    private val history = mutableListOf<Pair<String, String>>()
    private var exec: ExecutorService? = null
    private var voice: VoiceController? = null
    private var active = false
    private var thinking = false

    private val cCard = Color.parseColor("#F2101420")
    private val cBlue = Color.parseColor("#3D8BFF")
    private val cPink = Color.parseColor("#FF4FA3")
    private val cDim = Color.parseColor("#8E98B8")

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val wave = WaveView(ctx, cBlue, cPink)
    private val status = TextView(ctx)
    private val userText = TextView(ctx)
    private val replyText = TextView(ctx)
    private val scroll: ScrollView
    val view: View

    private val autoClose = Runnable { onClose() }

    init {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(16))
            background = GradientDrawable().apply {
                setColor(cCard)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
            }
            elevation = dp(8).toFloat()
        }

        val top = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(wave, LinearLayout.LayoutParams(dp(40), dp(26)))
        status.apply { setTextColor(cDim); textSize = 13f; text = "Jarvis" }
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
        top.addView(icon(android.R.drawable.ic_btn_speak_now) {
            if (voice != null) { cancelAutoClose(); voice?.startListening() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        top.addView(icon(android.R.drawable.ic_menu_close_clear_cancel) { onClose() },
            LinearLayout.LayoutParams(dp(40), dp(40)))
        card.addView(top)

        userText.apply {
            setTextColor(cDim); textSize = 13f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        replyText.apply { setTextColor(Color.WHITE); textSize = 17f; setLineSpacing(0f, 1.15f); visibility = View.GONE }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(userText)
        texts.addView(replyText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        scroll = MaxHeightScroll(ctx, (ctx.resources.displayMetrics.heightPixels * 0.4f).toInt()).apply {
            addView(texts)
            visibility = View.GONE
        }
        card.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10); marginEnd = dp(4)
        })

        view = FrameLayout(ctx).apply {
            setPadding(dp(12), dp(12), dp(12), dp(14))
            addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun icon(res: Int, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        alpha = 0.8f
        setPadding(dp(9), dp(9), dp(9), dp(9))
        setOnClickListener { onClick() }
    }

    // ---- hayotiy sikl ----

    fun start() {
        if (active) return
        active = true
        thinking = false
        WakeWordService.pause()
        exec = Executors.newSingleThreadExecutor()
        cancelAutoClose()
        userText.visibility = View.GONE
        replyText.text = ""
        scroll.visibility = View.GONE

        view.alpha = 0f
        view.translationY = dp(60).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(220).start()

        voice?.destroy()
        voice = VoiceController(
            context = appCtx,
            onResult = { handleText(it) },
            onListening = { setListening(it) },
            onError = { showReply(it, speak = false); scheduleAutoClose(8000) },
            onRms = { rms -> wave.level = ((rms + 2f) / 12f).coerceIn(0.15f, 1f) },
        )
        voice?.startListening()
    }

    fun stop() {
        if (!active) return
        active = false
        cancelAutoClose()
        main.removeCallbacksAndMessages(null)
        voice?.destroy()
        voice = null
        exec?.shutdownNow()
        exec = null
        view.animate().cancel()
        WakeWordService.resume()
    }

    // ---- holatlar ----

    private fun setListening(on: Boolean) {
        if (!active) return
        if (on) {
            cancelAutoClose()
            status.text = "Eshityapman…"
            status.setTextColor(cPink)
            wave.level = 0.5f
        } else {
            wave.level = 0.15f
            if (!thinking) {
                status.text = "Jarvis"
                status.setTextColor(cDim)
                scheduleAutoClose(8000)
            }
        }
    }

    private fun showReply(text: String, speak: Boolean, onSpoken: (() -> Unit)? = null) {
        thinking = false
        status.text = "Jarvis"
        status.setTextColor(cDim)
        wave.level = 0.15f
        replyText.text = text
        replyText.visibility = View.VISIBLE
        scroll.visibility = View.VISIBLE
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        if (speak) voice?.speak(text, onSpoken?.let { cb -> { main.post(cb) } })
    }

    private fun handleText(text: String) {
        if (!active) return
        cancelAutoClose()
        userText.text = text
        userText.visibility = View.VISIBLE
        replyText.text = ""
        replyText.visibility = View.GONE
        scroll.visibility = View.VISIBLE

        CommandRouter.fastPath(appCtx, text)?.let { finish(it); return }

        thinking = true
        status.text = "O'ylayapman…"
        status.setTextColor(cBlue)
        wave.level = 0.3f
        history.add("user" to text)
        val snapshot = history.toList()
        exec?.execute {
            val result = api.command(text, snapshot).map { CommandRouter.execute(appCtx, it, text) }
            main.post {
                if (!active) return@post
                result
                    .onSuccess { out ->
                        history.add("assistant" to out.message)
                        finish(out)
                    }
                    .onFailure {
                        showReply(it.message ?: "Noma'lum xato", speak = false)
                        scheduleAutoClose(10000)
                    }
            }
        }
    }

    private fun finish(out: CommandRouter.Outcome) {
        val goal = out.agentGoal
        val after = out.afterClose
        if (out.followUp) {
            // Jarvis savol berdi: gapirib bo'lgach javobingizni darhol tinglaydi
            showReply(out.message, out.speak) { if (active) voice?.startListening() }
            scheduleAutoClose(30000)
            return
        }
        showReply(out.message, out.speak)
        when {
            goal != null -> main.postDelayed({
                onClose()
                AgentRunner.run(appCtx, goal)
            }, 900)
            after != null -> main.postDelayed({
                onClose()
                main.postDelayed({ after() }, 500)
            }, 900)
            out.close -> scheduleAutoClose(1600)
            else -> scheduleAutoClose(15000)
        }
    }

    private fun scheduleAutoClose(ms: Long) {
        main.removeCallbacks(autoClose)
        main.postDelayed(autoClose, ms)
    }

    private fun cancelAutoClose() = main.removeCallbacks(autoClose)

    // ---- yordamchi view'lar ----

    private class MaxHeightScroll(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
        }
    }

    /** Ovoz to'lqini: 5 ta yumaloq ustun, ko'k → pushti gradient. */
    private class WaveView(ctx: Context, private val c1: Int, private val c2: Int) : View(ctx) {
        @Volatile var level = 0.15f
        private var shown = 0.15f
        private var phase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val anim = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, c1, c2, Shader.TileMode.CLAMP)
        }

        override fun onDraw(canvas: Canvas) {
            shown += (level - shown) * 0.2f
            val n = 5
            val bw = width / (n * 2f - 1f)
            for (i in 0 until n) {
                val wobble = 0.5f + 0.5f * sin(phase + i * 0.9f)
                val h = height * (0.22f + 0.78f * shown * (0.35f + 0.65f * wobble))
                val left = i * bw * 2f
                val top = (height - h) / 2f
                rect.set(left, top, left + bw, top + h)
                canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
            }
        }
    }
}
