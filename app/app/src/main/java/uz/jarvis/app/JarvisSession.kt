package uz.jarvis.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Yon tugma uzoq bosilganda ekranning pastida chiqadigan Jarvis paneli (Gemini kabi).
 * Ilova ochilmaydi — panel hozirgi ekran ustida ko'rinadi.
 */
class JarvisSession(ctx: Context) : VoiceInteractionSession(ctx) {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val api = JarvisApi()
    private val history = mutableListOf<Pair<String, String>>()
    private var voice: VoiceController? = null

    private lateinit var orb: View
    private lateinit var status: TextView
    private lateinit var userText: TextView
    private lateinit var replyText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var mic: TextView
    private var orbAnim: ObjectAnimator? = null
    private var listening = false

    private val cBg = Color.parseColor("#0B0F1A")
    private val cSurface = Color.parseColor("#121A2B")
    private val cBlue = Color.parseColor("#2D7DFF")
    private val cPink = Color.parseColor("#FF4FA3")
    private val cDim = Color.parseColor("#8A94A8")

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    override fun onCreateContentView(): View {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Panel tashqarisiga bosilsa yopiladi
            setOnClickListener { hide() }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(cBg)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#592D7DFF"))
            }
            isClickable = true // karta ichiga bosish yopmasin
        }

        // Yuqori qator: sharcha + sarlavha + yopish
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        orb = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cBlue, cPink)).apply {
                shape = GradientDrawable.OVAL
            }
        }
        header.addView(orb, LinearLayout.LayoutParams(dp(26), dp(26)))
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(context).apply {
            text = "JARVIS"; setTextColor(Color.WHITE); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.25f
        })
        status = TextView(context).apply { setTextColor(cDim); textSize = 12f; text = "Tayyor" }
        titles.addView(status)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
        header.addView(TextView(context).apply {
            text = "✕"; setTextColor(cDim); textSize = 18f
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { hide() }
        })
        card.addView(header)

        // Matn: foydalanuvchi so'zi va Jarvis javobi
        userText = TextView(context).apply { setTextColor(cDim); textSize = 14f; visibility = View.GONE }
        replyText = TextView(context).apply { setTextColor(Color.WHITE); textSize = 17f; setLineSpacing(0f, 1.15f) }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(userText)
        texts.addView(replyText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        scroll = ScrollView(context).apply { addView(texts) }
        card.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        // Mikrofon tugmasi (qayta gapirish uchun)
        mic = TextView(context).apply {
            text = "🎤  Gapirish"; setTextColor(Color.WHITE); textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cBlue, cPink)).apply {
                cornerRadius = dp(26).toFloat()
            }
            setOnClickListener { if (listening) voice?.stop() else startListening() }
        }
        card.addView(mic, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(16) })

        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { setMargins(dp(10), dp(10), dp(10), dp(10)) })

        // Navigatsiya paneli ostida qolib ketmasligi uchun
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
        userText.visibility = View.GONE
        replyText.text = ""
        voice?.destroy()
        voice = VoiceController(
            context = context.applicationContext,
            onResult = { handleText(it) },
            onListening = { setListening(it) },
            onError = { showReply(it) },
        )
        startListening()
    }

    override fun onHide() {
        super.onHide()
        orbAnim?.cancel()
        voice?.destroy()
        voice = null
        listening = false
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun startListening() {
        replyText.text = ""
        voice?.startListening()
    }

    private fun setListening(on: Boolean) {
        listening = on
        mic.text = if (on) "⏹  To'xtatish" else "🎤  Gapirish"
        status.text = if (on) "Eshityapman…" else status.text
        status.setTextColor(if (on) cPink else cDim)
        orbAnim?.cancel()
        orb.scaleX = 1f; orb.scaleY = 1f
        if (on) {
            orbAnim = ObjectAnimator.ofFloat(orb, View.SCALE_X, 1f, 1.35f).apply {
                duration = 600; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { orb.scaleY = orb.scaleX }
                start()
            }
        }
    }

    private fun showReply(text: String) {
        status.text = "Jarvis"
        status.setTextColor(cDim)
        replyText.text = text
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun handleText(text: String) {
        userText.text = text
        userText.visibility = View.VISIBLE
        replyText.text = ""

        // 1) Ilova ochish buyrug'i — serverga bormaydi, panel yopiladi
        val appName = AppLauncher.parseOpenCommand(text)
        if (appName != null) {
            val opened = AppLauncher.open(context, appName)
            if (opened != null) {
                showReply("$opened ochilmoqda")
                main.postDelayed({ hide() }, 400)
            } else {
                val msg = "\"$appName\" nomli ilova topilmadi."
                showReply(msg); voice?.speak(msg)
            }
            return
        }

        // 2) Aks holda serverdagi AI ga yuboramiz
        status.text = "O'ylayapman…"
        history.add("user" to text)
        val snapshot = history.toList()
        executor.execute {
            val result = api.chat(snapshot)
            main.post {
                result
                    .onSuccess { history.add("assistant" to it); showReply(it); voice?.speak(it) }
                    .onFailure { showReply(it.message ?: "Noma'lum xato") }
            }
        }
    }
}
