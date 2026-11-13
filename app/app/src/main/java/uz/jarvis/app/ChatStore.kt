package uz.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Suhbatlarni telefonning ichki xotirasida JSON fayl sifatida saqlaydi (internet kerak emas). */
object ChatStore {
    private const val FILE = "chats.json"

    fun load(ctx: Context): List<Chat> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                val ms = c.optJSONArray("messages") ?: JSONArray()
                Chat(
                    id = c.getString("id"),
                    title = c.optString("title", "Suhbat"),
                    updatedAt = c.optLong("updatedAt", 0L),
                    messages = (0 until ms.length()).map { j ->
                        val m = ms.getJSONObject(j)
                        Msg(
                            id = m.getString("id"),
                            text = m.optString("text"),
                            fromUser = m.optBoolean("fromUser"),
                            isError = m.optBoolean("isError"),
                            reaction = m.optInt("reaction", 0),
                            saved = m.optBoolean("saved"),
                        )
                    },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, chats: List<Chat>) {
        runCatching {
            val arr = JSONArray()
            chats.forEach { c ->
                val ms = JSONArray()
                c.messages.forEach { m ->
                    ms.put(
                        JSONObject()
                            .put("id", m.id).put("text", m.text).put("fromUser", m.fromUser)
                            .put("isError", m.isError).put("reaction", m.reaction).put("saved", m.saved)
                    )
                }
                arr.put(
                    JSONObject().put("id", c.id).put("title", c.title)
                        .put("updatedAt", c.updatedAt).put("messages", ms)
                )
            }
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(File(ctx.filesDir, FILE))
        }
    }
}
