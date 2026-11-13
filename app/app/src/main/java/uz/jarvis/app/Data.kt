package uz.jarvis.app

import java.util.UUID

/** reaction: 1 = like, -1 = dislike, 0 = yo'q */
data class Msg(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
    val reaction: Int = 0,
    val saved: Boolean = false,
)

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Yangi suhbat",
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Msg> = emptyList(),
)
