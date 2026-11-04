package uz.jarvis.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JarvisApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // bepul serverlar uyg'onishi uchun
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** history: (role, content) juftliklari; role = "user" yoki "assistant". Bloklovchi — IO oqimida chaqiring. */
    fun chat(history: List<Pair<String, String>>): Result<String> = runCatching {
        val arr = JSONArray()
        history.takeLast(20).forEach { (role, content) ->
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject().put("messages", arr).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(BuildConfig.SERVER_URL + "/chat")
            .post(body)
            .apply { if (BuildConfig.APP_KEY.isNotEmpty()) header("x-app-key", BuildConfig.APP_KEY) }
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!resp.isSuccessful) {
                error(json?.optString("error")?.ifEmpty { null } ?: "Server xatosi (${resp.code})")
            }
            json?.optString("reply")?.ifEmpty { null } ?: error("Bo'sh javob")
        }
    }
}
