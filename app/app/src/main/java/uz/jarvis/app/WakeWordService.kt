package uz.jarvis.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Doimiy tinglash: "Jarvis" so'zini internetsiz (Vosk), kalitsiz aniqlaydi va panelni ochadi.
 * Ovoz telefondan tashqariga chiqmaydi.
 */
class WakeWordService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "JarvisWake"
        private const val CHANNEL = "jarvis_listen"
        private const val NOTIF_ID = 1

        @Volatile private var instance: WakeWordService? = null

        fun isRunning() = instance != null

        fun start(ctx: Context) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Xizmatni boshlab bo'lmadi", e)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeWordService::class.java))
        }

        /** Panel mikrofonni ishlatayotganda tinglashni to'xtatib turadi. */
        fun pause() { instance?.pauseListening() }
        fun resume() { instance?.resumeListening() }
    }

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var paused = false
    private var lastTrigger = 0L
    private var loading = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        val n = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground xatosi", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (model == null && !loading) {
            loading = true
            StorageService.unpack(
                this, "model-en-us", "model",
                { m: Model ->
                    loading = false
                    model = m
                    main.post { startRecognition() }
                },
                { e: IOException ->
                    loading = false
                    Log.e(TAG, "Model yuklanmadi", e)
                },
            )
        } else if (model != null && speech == null) {
            startRecognition()
        }
        return START_STICKY
    }

    private fun startRecognition() {
        val m = model ?: return
        if (paused || speech != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        try {
            val rec = Recognizer(m, 16000f, "[\"jarvis\", \"[unk]\"]")
            speech = SpeechService(rec, 16000f).also { it.startListening(this) }
        } catch (e: Exception) {
            Log.e(TAG, "Tinglashni boshlab bo'lmadi", e)
            speech = null
            main.postDelayed({ startRecognition() }, 5000)
        }
    }

    private fun pauseListening() {
        main.post {
            paused = true
            speech?.shutdown()
            speech = null
        }
    }

    private fun resumeListening() {
        main.postDelayed({
            paused = false
            startRecognition()
        }, 800)
    }

    private fun heard(hypothesis: String?) {
        if (paused || hypothesis == null || !hypothesis.contains("jarvis")) return
        val now = System.currentTimeMillis()
        if (now - lastTrigger < 3000) return
        lastTrigger = now
        main.post {
            pauseListening()
            val shown = OverlayController.showPanel(applicationContext)
            if (!shown) {
                // Overlay ruxsati yo'q — ilovani ochib tinglashni boshlaymiz
                try {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_AUTO_LISTEN, true)
                    })
                } catch (_: Exception) {}
                resumeListening()
            }
        }
    }

    // ---- Vosk RecognitionListener (asosiy oqimda chaqiriladi) ----
    override fun onPartialResult(hypothesis: String?) = heard(hypothesis)
    override fun onResult(hypothesis: String?) = heard(hypothesis)
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk xatosi", exception)
        speech?.shutdown()
        speech = null
        main.postDelayed({ startRecognition() }, 3000)
    }
    override fun onTimeout() {}

    override fun onDestroy() {
        instance = null
        main.removeCallbacksAndMessages(null)
        speech?.shutdown()
        speech = null
        model?.close()
        model = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Jarvis tinglayapti", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Jarvis")
            .setContentText("\"Jarvis\" deb chaqiring")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
