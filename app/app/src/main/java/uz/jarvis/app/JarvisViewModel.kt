package uz.jarvis.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Msg(val text: String, val fromUser: Boolean, val isError: Boolean = false)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val api = JarvisApi()

    private val _messages = MutableStateFlow(
        listOf(Msg("Salom! Men Jarvis. Yozing yoki mikrofonni bosib gapiring. Masalan: \"Telegramni och\".", false))
    )
    val messages = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _speak = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val speakEvents: SharedFlow<String> = _speak.asSharedFlow()

    private fun add(m: Msg) { _messages.value = _messages.value + m }

    private fun reply(text: String, fromVoice: Boolean) {
        add(Msg(text, false))
        if (fromVoice) _speak.tryEmit(text)
    }

    fun showError(text: String) = add(Msg(text, false, isError = true))

    fun send(input: String, fromVoice: Boolean = false) {
        val text = input.trim()
        if (text.isEmpty() || _loading.value) return
        add(Msg(text, true))

        // 1) Avval ilova ochish buyrug'i ekanini tekshiramiz (serverga bormaydi)
        val appName = AppLauncher.parseOpenCommand(text)
        if (appName != null) {
            val opened = AppLauncher.open(getApplication(), appName)
            if (opened != null) reply("$opened ochilmoqda", fromVoice)
            else reply("\"$appName\" nomli ilova topilmadi.", fromVoice)
            return
        }

        // 2) Aks holda serverdagi AI ga yuboramiz
        val history = _messages.value
            .filter { !it.isError }
            .map { (if (it.fromUser) "user" else "assistant") to it.text }
            .dropWhile { it.first != "user" }

        _loading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { api.chat(history) }
            _loading.value = false
            result
                .onSuccess { reply(it, fromVoice) }
                .onFailure { showError(it.message ?: "Noma'lum xato") }
        }
    }
}
