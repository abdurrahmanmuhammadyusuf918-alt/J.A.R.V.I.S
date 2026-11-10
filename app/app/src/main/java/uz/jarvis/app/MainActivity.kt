package uz.jarvis.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uz.jarvis.app.ui.ChatScreen
import uz.jarvis.app.ui.JarvisTheme
import uz.jarvis.app.ui.SetupScreen

class MainActivity : ComponentActivity() {

    // Yon tugma bosilganda oshadi; ChatScreen shunda mikrofonni yoqadi
    private var listenTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            JarvisTheme {
                var setup by remember { mutableStateOf(!Prefs.setupDone(this@MainActivity)) }
                if (setup) {
                    SetupScreen(onDone = { setup = false })
                } else {
                    ChatScreen(autoListenTrigger = listenTrigger, onOpenSetup = { setup = true })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Doimiy tinglashni yoqamiz (ruxsat berilgan bo'lsa)
        if (Prefs.wakeEnabled(this) && Perms.granted(this, Manifest.permission.RECORD_AUDIO) &&
            !WakeWordService.isRunning()
        ) WakeWordService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_LISTEN, false) == true) {
            intent.removeExtra(EXTRA_AUTO_LISTEN)
            listenTrigger++
        }
    }

    companion object {
        const val EXTRA_AUTO_LISTEN = "uz.jarvis.app.AUTO_LISTEN"
    }
}
