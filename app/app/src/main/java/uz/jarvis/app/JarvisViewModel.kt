package uz.jarvis.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val saveMutex = Mutex()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    val messages: StateFlow<List<Msg>> = combine(_chats, _currentId) { cs, id ->
        cs.firstOrNull { it.id == id }?.messages ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val saved: StateFlow<List<Msg>> = _chats.map { cs ->
        cs.flatMap { it.messages }.filter { it.saved }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _speak = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val speakEvents: SharedFlow<String> = _speak.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { ChatStore.load(getApplication()) }
            _chats.value = (_chats.value + loaded).distinctBy { it.id }
        }
    }

    // ---- saqlash ----

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock { ChatStore.save(getApplication(), _chats.value) }
        }
    }

    private fun updateChat(id: String, transform: (Chat) -> Chat) {
        _chats.value = _chats.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun mapMessage(msgId: String, f: (Msg) -> Msg) {
        _chats.value = _chats.value.map { c ->
            if (c.messages.any { it.id == msgId })
                c.copy(messages = c.messages.map { if (it.id == msgId) f(it) else it })
            else c
        }
        persist()
    }

    private fun titleFrom(text: String): String {
        val t = text.trim().replace('\n', ' ')
        return if (t.length > 40) t.take(40) + "…" else t
    }

    /** Joriy suhbat yo'q bo'lsa, yangisini yaratadi. */
    private fun ensureChat(hint: String): String {
        _currentId.value?.let { id -> if (_chats.value.any { it.id == id }) return id }
        val chat = Chat(title = titleFrom(hint).ifBlank { "Yangi suhbat" })
        _chats.value = listOf(chat) + _chats.value
        _currentId.value = chat.id
        return chat.id
    }

    private fun add(chatId: String, m: Msg) =
        updateChat(chatId) { it.copy(messages = it.messages + m, updatedAt = System.currentTimeMillis()) }

    // ---- suhbatlar ----

    fun newChat() { _currentId.value = null }

    fun openChat(id: String) { _currentId.value = id }

    fun deleteChat(id: String) {
        _chats.value = _chats.value.filterNot { it.id == id }
        if (_currentId.value == id) _currentId.value = null
        persist()
    }

    fun deleteAll() {
        _chats.value = emptyList()
        _currentId.value = null
        persist()
    }

    // ---- xabar tugmalari ----

    fun react(msgId: String, value: Int) = mapMessage(msgId) {
        it.copy(reaction = if (it.reaction == value) 0 else value)
    }

    fun toggleSave(msgId: String) = mapMessage(msgId) { it.copy(saved = !it.saved) }

    // ---- yuborish ----

    fun showError(text: String) {
        val id = ensureChat("Yangi suhbat")
        add(id, Msg(text = text, fromUser = false, isError = true))
    }

    private fun reply(chatId: String, text: String, fromVoice: Boolean) {
        add(chatId, Msg(text = text, fromUser = false))
        if (fromVoice) _speak.tryEmit(text)
    }

    fun send(input: String, fromVoice: Boolean = false) {
        val text = input.trim()
        if (text.isEmpty() || _loading.value) return
        val chatId = ensureChat(text)
        add(chatId, Msg(text = text, fromUser = true))

        // 1) Tezkor yo'l: "Telegramni och", "Ayamga qo'ng'iroq qil" (serverga bormaydi)
        val fast = CommandRouter.fastPath(getApplication(), text)
        if (fast != null) {
            reply(chatId, fast.message, fromVoice)
            return
        }

        // 2) Aks holda serverdagi AI buyruqni tushunadi va bajaradi
        val history = (_chats.value.firstOrNull { it.id == chatId }?.messages ?: emptyList())
            .filter { !it.isError }
            .map { (if (it.fromUser) "user" else "assistant") to it.text }
            .dropWhile { it.first != "user" }

        _loading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                JarvisApi().command(text, history).map { CommandRouter.execute(getApplication(), it) }
            }
            _loading.value = false
            result
                .onSuccess { out ->
                    reply(chatId, out.message, fromVoice || (out.speak && out.agentGoal != null))
                    out.agentGoal?.let { AgentRunner.run(getApplication(), it) }
                }
                .onFailure { add(chatId, Msg(text = it.message ?: "Noma'lum xato", fromUser = false, isError = true)) }
        }
    }
}
