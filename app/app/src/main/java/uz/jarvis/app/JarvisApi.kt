package uz.jarvis.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class JarvisApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // bepul serverlar uyg'onishi uchun
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(BuildConfig.SERVER_URL + path)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (BuildConfig.APP_KEY.isNotEmpty()) header("x-app-key", BuildConfig.APP_KEY) }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!resp.isSuccessful) {
                error(json?.optString("error")?.ifEmpty { null } ?: "Server xatosi (${resp.code})")
            }
            return json ?: error("Bo'sh javob")
        }
    }

    private fun messages(history: List<Pair<String, String>>): JSONArray {
        val arr = JSONArray()
        history.takeLast(20).forEach { (role, content) ->
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        return arr
    }

    /** history: (role, content) juftliklari; role = "user" yoki "assistant". Bloklovchi — IO oqimida chaqiring. */
    fun chat(history: List<Pair<String, String>>): Result<String> = runCatching {
        post("/chat", JSONObject().put("messages", messages(history)))
            .optString("reply").ifEmpty { null } ?: error("Bo'sh javob")
    }

    /** Ovozli buyruqni amalga aylantiradi (call, sms, open_app, agent, chat ...). */
    fun command(text: String, history: List<Pair<String, String>>): Result<JSONObject> = runCatching {
        post(
            "/command",
            JSONObject().put("text", text).put("history", messages(history)).put("now", nowString())
        )
    }

    /** Ob-havo: shahar nomi yoki koordinata bo'yicha qisqa javob. */
    fun weather(city: String, lat: Double?, lon: Double?, question: String): Result<String> = runCatching {
        val body = JSONObject().put("city", city).put("question", question).put("now", nowString())
        if (lat != null && lon != null) body.put("lat", lat).put("lon", lon)
        post("/weather", body).optString("reply").ifEmpty { null } ?: error("Bo'sh javob")
    }

    private fun nowString(): String =
        SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.US).format(Date()) + " (" + TimeZone.getDefault().id + ")"

    /** Agent uchun keyingi qadamni so'raydi. */
    fun agent(goal: String, screen: String, steps: List<String>, pkg: String): Result<JSONObject> = runCatching {
        post(
            "/agent",
            JSONObject()
                .put("goal", goal)
                .put("screen", screen)
                .put("history", JSONArray(steps))
                .put("app", pkg)
        )
    }
}
