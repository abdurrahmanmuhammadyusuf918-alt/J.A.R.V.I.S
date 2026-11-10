package uz.jarvis.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import uz.jarvis.app.ui.ChatScreen
import uz.jarvis.app.ui.JarvisTheme

class MainActivity : ComponentActivity() {

    // Yon tugma bosilganda oshadi; ChatScreen shunda mikrofonni yoqadi
    private var listenTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            JarvisTheme { ChatScreen(autoListenTrigger = listenTrigger) }
        }
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
