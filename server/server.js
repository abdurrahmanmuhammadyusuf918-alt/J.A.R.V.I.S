import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";

const {
  GEMINI_API_KEY,
  GEMINI_MODEL = "gemini-3.6-flash",
  APP_KEY = "",
  PORT = 3000,
} = process.env;

if (!GEMINI_API_KEY) {
  console.error("GEMINI_API_KEY topilmadi. .env yoki hosting sozlamalariga qo'shing.");
  process.exit(1);
}

const SYSTEM_PROMPT =
  "Sen Jarvis — foydalanuvchining shaxsiy AI yordamchisisan. " +
  "Foydalanuvchi qaysi tilda yozsa, shu tilda (o'zbek, rus, ingliz) javob ber. " +
  "Javoblaring aniq, qisqa va foydali bo'lsin. Kod so'rasa, to'liq va ishlaydigan kod ber.";

const commandPrompt = (now) => `Sen Jarvis — Android telefondagi ovozli yordamchisan (Siri kabi aqlli). Hozirgi vaqt: ${now}.
Foydalanuvchi gapirgan matnni (ovozdan tanilgan, imlo xatosi bo'lishi mumkin; o'zbek, rus yoki ingliz tilida) tahlil qilib, FAQAT bitta JSON obyekt qaytar. Boshqa matn yozma.

Umumiy maydonlar: "action" va "say" — bajarishdan oldin ovozda aytiladigan juda qisqa tasdiq (foydalanuvchi tilida), masalan "Shahzodaga qo'ng'iroq qilyapman". "chat" uchun "say" kerak emas.

"action" qiymatlari va ularning maydonlari:
Muloqot:
- "chat": oddiy savol, suhbat, tarjima, hisob-kitob, ta'rif, maslahat, hazil, umumiy bilim. "reply" ga javobni yoz (qisqa, foydalanuvchi tilida; ovozda o'qiladi, shuning uchun markdown, emoji, ro'yxat belgilarisiz). Foydalanuvchidan biror narsani aniqlashtirishing kerak bo'lsa, savolni "reply" ga yozib "followup": true qo'y.
- "web": internetdan YANGI yoki o'zgaruvchan ma'lumot kerak (yangiliklar, sport natijalari, valyuta kursi, narxlar, bugungi voqealar, hozirgi lavozimdagi shaxs, film/konsert vaqtlari). "query" ga qidiruv matni.
- "weather": ob-havo savoli. "city" — shahar aytilgan bo'lsa (ingliz yoki asl yozuvida), aytilmagan bo'lsa bo'sh qoldir.
Aloqa:
- "call": qo'ng'iroq. "contact" (faqat ism) yoki "number".
- "sms": oddiy SMS. "contact" yoki "number", "message" (yuboriladigan matn). Ilova nomi aytilmagan bo'lsa shuni ishlat.
- "email": xat yozish. "to" (email manzil yoki kontakt ismi), "subject", "body".
- "app_task": aniq bir ilova ichida ish (Telegram, Plus, WhatsApp'da kimgadir xabar yozish va hokazo). "app" va "goal" (ishning to'liq, aniq tavsifi; masalan: "Shahzoda ismli kontaktga kirib 'salom' deb yoz va yubor").
Ilovalar va media:
- "open_app": ilovani ochish. "app".
- "youtube": YouTube'da qidirish/ijro. "query", "play" (true bo'lsa birinchi videoni ijro etadi).
- "music": qo'shiq/ijrochi/albom qo'yish. "query", "app" (aytilgan bo'lsa: "spotify" yoki "youtube music").
- "media": hozir ijro bo'layotgan narsani boshqarish. "key": "play" | "pause" | "next" | "previous" | "stop".
- "open_url": saytni ochish. "url".
- "navigate": xaritada joy/yo'nalish. "query".
Vaqt va reja:
- "alarm": budilnik. "hour" (0-23), "minute" (0-59), "label".
- "timer": taymer. "seconds", "label".
- "calendar_add": kalendarga voqea yoki ESLATMA ("eslatib qo'y", "remind me", "uchrashuv qo'sh"). "title", "start" ("yyyy-MM-ddTHH:mm", mahalliy vaqt; "ertaga", "juma kuni" kabilarni hozirgi vaqtga nisbatan hisobla), "end" (ixtiyoriy).
- "calendar_read": reja/jadval so'rash ("bugun nima rejam bor"). "from" va "to" ("yyyy-MM-ddTHH:mm"; bir kun uchun 00:00 dan 23:59 gacha).
- "info": "kind": "time" (soat nechchi) | "date" (bugun nechanchi, qaysi kun) | "battery" (batareya).
Telefon boshqaruvi:
- "flashlight": fonar. "on" (true/false).
- "volume": ovoz balandligi. "mode": "up" | "down" | "set" | "mute" | "unmute" | "max"; "level" (0-100, faqat "set" uchun); "stream": "music" (standart) yoki "ring" (qo'ng'iroq ovozi).
- "brightness": ekran yorqinligi. "mode": "up" | "down" | "set" | "max" | "min"; "level" (0-100).
- "camera": "mode": "photo" | "selfie" | "video".
- "settings": sozlama sahifasini ochish. "target": "wifi" | "bluetooth" | "airplane" | "hotspot" | "location" | "dnd" | "display" | "battery" | "nfc" | "sound" | "data" | "storage" | "apps" | "accessibility" | "date" | "language" | "security" | "about" | "main". Yoqish/o'chirish so'ralgan bo'lsa "on": true yoki false qo'sh (Wi-Fi'ni yoq, Bluetooth'ni o'chir va hokazo).
- "global": tizim amali. "name": "screenshot" | "lock" | "home" | "back" | "recents" | "notifications" | "quick_settings" | "power".
- "random": "kind": "coin" (tanga) | "dice" (kubik) | "number" (son, "min" va "max" bilan).
- "agent": yuqoridagilarga to'g'ri kelmaydigan, lekin telefonda bajarsa bo'ladigan boshqa ish. "goal" ga to'liq tavsif yoz.

Qoidalar:
- Qo'ng'iroq so'ralsa ("telefonga kirib Ayam kontaktiga qo'ng'iroq qil", "Ayamga qo'ng'iroq qil", "call mom") har doim "call" ishlat, "app_task" emas; "contact" ga faqat ismni yoz.
- Ilova nomisiz "SMS yoz" / "xabar yoz" bo'lsa "sms".
- Ob-havo — "weather", yangi ma'lumot — "web", oddiy bilim savoli — "chat".
- Vaqtni so'rasa "info" ishlat, o'zing taxmin qilma.
- "Jarvis" so'zini e'tiborsiz qoldir. Ism lotin harfida, foydalanuvchi aytganidek yoz.
- Faqat JSON.`;

const WEB_PROMPT = (now) => `Sen Jarvis — ovozli yordamchisan. Hozirgi vaqt: ${now}. Internet qidiruvidan topilgan yangi ma'lumotga tayanib, savolga qisqa (2-4 gap), aniq javob ber. Javob ovozda o'qiladi: markdown, emoji, ro'yxat belgilari va uzun havolalar ishlatma. Foydalanuvchi qaysi tilda so'ragan bo'lsa, shu tilda javob ber. Ishonchsiz bo'lsang, shuni ayt.`;

const WEATHER_PROMPT = `Sen Jarvis — ovozli yordamchisan. Quyida Open-Meteo'dan olingan ob-havo ma'lumoti berilgan (weather_code — WMO kodlari: 0 ochiq, 1-3 qisman bulutli/bulutli, 45-48 tuman, 51-57 yengil yomg'ir/shivalash, 61-67 yomg'ir, 71-77 qor, 80-82 jala, 95-99 momaqaldiroq). Foydalanuvchining savoliga (bugun, ertaga yoki bir necha kun) qisqa (2-3 gap), tabiiy javob ber: harorat (°C), osmon holati, yog'ingarchilik ehtimoli, kerak bo'lsa maslahat (soyabon, issiq kiyim). Javob ovozda o'qiladi: markdown, emoji, ro'yxatsiz. Foydalanuvchi tilida javob ber.`;

const AGENT_PROMPT = `Sen Android telefonni boshqaruvchi agentsan. Maqsadga erishish uchun ekrandagi elementlar ustida BITTA qadam tanla.

Ekran elementlari "[id] tur: matn" ko'rinishida beriladi. Bayroqlar: click=bosiladi, edit=matn kiritiladi, scroll=aylantiriladi, focused=fokusda.

FAQAT bitta JSON obyekt qaytar:
{"action": "click"|"type"|"scroll"|"back"|"home"|"notifications"|"quick_settings"|"recents"|"screenshot"|"lock"|"open_app"|"wait"|"done"|"fail", "id": <element id>, "text": "<kiritiladigan matn>", "direction": "down"|"up", "say": "<done/fail bo'lganda foydalanuvchiga qisqa xabar>"}

Qoidalar:
- "open_app" uchun "text" ga ilova nomini yoz (masalan "Telegram", "Sozlamalar").
- "click" va "type" uchun "id" majburiy. "type" matnni shu edit maydoniga yozadi.
- Kerakli element ko'rinmasa "scroll" qil yoki qidiruv maydonidan foydalan.
- Xabar yuborishda: kontaktni toping, ochib chat maydoniga yozing, keyin "Yuborish/Send" tugmasini bosing.
- Avvalgi qadamlar tarixiga qarab, bir xil amalni takrorlama.
- Maqsad bajarilgan bo'lsa "done" va "say" ga natijani yoz. Imkonsiz bo'lsa "fail".
- To'lov, parol, o'chirish kabi xavfli amallarni foydalanuvchi aniq so'ramagan bo'lsa bajarma — "fail" qil.
Faqat JSON.`;

const app = express();
app.set("trust proxy", 1);
app.use(cors());
app.use(express.json({ limit: "200kb" }));

const limiter = (limit) =>
  rateLimit({
    windowMs: 60 * 1000,
    limit,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: "Juda ko'p so'rov. Biroz kutib turing." },
  });
app.use("/chat", limiter(20));
app.use("/command", limiter(30));
app.use("/agent", limiter(90));
app.use("/weather", limiter(20));

app.get("/health", (_req, res) => res.json({ ok: true, model: MODELS[preferred] }));

// Diagnostika: har bir modelni qisqa so'rov bilan tekshiradi (brauzerda oching: /diag)
app.get("/diag", async (req, res) => {
  if (!authorized(req, res)) return;
  const out = [];
  for (const model of MODELS) {
    try {
      const t = await geminiOnce(model, {
        system: "Qisqa javob ber.",
        contents: [{ role: "user", parts: [{ text: "Salom deb javob ber" }] }],
        maxOutputTokens: 20,
      });
      out.push({ model, ok: true, sample: t.slice(0, 40) });
    } catch (e) {
      out.push({ model, ok: false, status: e.geminiStatus ?? null, error: String(e.message).slice(0, 200) });
    }
  }
  res.json({ active: MODELS[preferred], results: out });
});

function authorized(req, res) {
  if (APP_KEY && req.get("x-app-key") !== APP_KEY) {
    res.status(401).json({ error: "Ruxsat yo'q." });
    return false;
  }
  return true;
}

// Model nomlari vaqt o'tib o'chirib turiladi (gemini-2.0-flash 2026-06-01 da o'chgan).
// Shuning uchun birinchi model ishlamasa, keyingisiga o'tamiz va ishlaganini eslab qolamiz.
const MODELS = [...new Set([GEMINI_MODEL, "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"])];
let preferred = 0;

async function geminiOnce(model, { system, contents, json = false, maxOutputTokens = 2048, temperature = 0.7, tools }) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const generationConfig = { temperature, maxOutputTokens };
  if (json) generationConfig.responseMimeType = "application/json";
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: system }] },
      contents,
      generationConfig,
      ...(tools ? { tools } : {}),
    }),
  });
  let data = {};
  try {
    data = await r.json();
  } catch {}
  if (!r.ok) {
    const err = new Error(data?.error?.message || `HTTP ${r.status}`);
    err.geminiStatus = r.status;
    err.model = model;
    throw err;
  }
  return (data.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
}

function friendlyError(e) {
  const st = e.geminiStatus;
  let msg;
  if (st === 429) msg = "Bepul limit tugadi, birozdan keyin urinib ko'ring.";
  else if (st === 404) msg = "AI modeli topilmadi (404). Server sozlamasini yangilash kerak.";
  else if (st === 401 || st === 403) msg = `API kalit yaroqsiz yoki ruxsat yo'q (${st}).`;
  else if (st === 503 || st === 500) msg = `AI xizmati band (${st}), birozdan keyin urinib ko'ring.`;
  else msg = `AI xizmatida xatolik${st ? ` (${st})` : ""}.`;
  const err = new Error(msg);
  err.status = 502;
  return err;
}

async function gemini(opts) {
  let last;
  for (let k = 0; k < MODELS.length; k++) {
    const idx = (preferred + k) % MODELS.length;
    try {
      const out = await geminiOnce(MODELS[idx], opts);
      preferred = idx;
      return out;
    } catch (e) {
      last = e;
      console.error(`Gemini xatosi [${e.model ?? MODELS[idx]}]:`, e.geminiStatus ?? "", e.message);
    }
  }
  throw friendlyError(last ?? new Error("noma'lum"));
}

function parseJson(raw) {
  const cleaned = raw.replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    const m = cleaned.match(/\{[\s\S]*\}/);
    if (m) {
      try {
        return JSON.parse(m[0]);
      } catch {}
    }
    return null;
  }
}

function toContents(messages) {
  return messages
    .slice(-20)
    .filter((m) => m && typeof m.content === "string" && m.content.trim())
    .map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content.slice(0, 4000) }],
    }));
}

app.post("/chat", async (req, res) => {
  if (!authorized(req, res)) return;
  const { messages } = req.body ?? {};
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: "messages massivi kerak." });
  }
  const contents = toContents(messages);
  if (contents.length === 0 || contents[contents.length - 1].role !== "user") {
    return res.status(400).json({ error: "Oxirgi xabar foydalanuvchidan bo'lishi kerak." });
  }
  try {
    const reply = await gemini({ system: SYSTEM_PROMPT, contents });
    res.json({ reply: reply || "Javob olinmadi, qayta urinib ko'ring." });
  } catch (e) {
    console.error(e);
    res.status(e.status ?? 500).json({ error: e.message || "Server xatosi." });
  }
});

// Ovozli buyruqni amalga aylantiradi: { text, history?, now? } -> { action, ... }
app.post("/command", async (req, res) => {
  if (!authorized(req, res)) return;
  const { text, history, now } = req.body ?? {};
  if (typeof text !== "string" || !text.trim()) {
    return res.status(400).json({ error: "text kerak." });
  }
  const nowStr = typeof now === "string" && now ? now.slice(0, 80) : new Date().toISOString();
  const contents = Array.isArray(history) ? toContents(history) : [];
  // history oxirgi xabar sifatida text ni ham o'z ichiga olmasa, qo'shamiz
  if (contents.length === 0 || contents[contents.length - 1].role !== "user") {
    contents.push({ role: "user", parts: [{ text: text.slice(0, 2000) }] });
  }
  try {
    const raw = await gemini({
      system: commandPrompt(nowStr),
      contents,
      json: true,
      maxOutputTokens: 1024,
      temperature: 0.3,
    });
    const obj = parseJson(raw);
    if (!obj || typeof obj.action !== "string") {
      return res.json({ action: "chat", reply: raw || "Tushunmadim, qayta ayting." });
    }

    // Internetdan yangi ma'lumot kerak: Google Search bilan javob beramiz
    if (obj.action === "web") {
      const q = String(obj.query || text).slice(0, 500);
      const prompt = `Foydalanuvchi savoli: ${text.slice(0, 1000)}\nQidiruv: ${q}`;
      let reply = "";
      try {
        reply = await gemini({
          system: WEB_PROMPT(nowStr),
          contents: [{ role: "user", parts: [{ text: prompt }] }],
          tools: [{ google_search: {} }],
          maxOutputTokens: 700,
          temperature: 0.3,
        });
      } catch (e) {
        console.error("Qidiruv bilan javob xatosi:", e.message);
      }
      if (!reply) {
        try {
          reply = await gemini({
            system: WEB_PROMPT(nowStr) + " Internet mavjud emas; bilganingcha javob ber va ma'lumot eskirgan bo'lishi mumkinligini ayt.",
            contents: [{ role: "user", parts: [{ text: prompt }] }],
            maxOutputTokens: 500,
            temperature: 0.3,
          });
        } catch (e) {
          console.error(e.message);
        }
      }
      return res.json({ action: "chat", reply: reply || "Ma'lumot topa olmadim, birozdan keyin urinib ko'ring." });
    }

    res.json(obj);
  } catch (e) {
    console.error(e);
    res.status(e.status ?? 500).json({ error: e.message || "Server xatosi." });
  }
});

// Ob-havo: { city?, lat?, lon?, question, now } -> { reply }
app.post("/weather", async (req, res) => {
  if (!authorized(req, res)) return;
  const { city, lat, lon, question, now } = req.body ?? {};
  try {
    let latitude = Number(lat);
    let longitude = Number(lon);
    let place = "";
    if (typeof city === "string" && city.trim()) {
      const gr = await fetch(
        `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city.trim().slice(0, 100))}&count=1&language=en&format=json`
      );
      const g = await gr.json();
      const first = g?.results?.[0];
      if (!first) return res.json({ reply: `"${city.trim()}" nomli shahar topilmadi.` });
      latitude = first.latitude;
      longitude = first.longitude;
      place = first.name + (first.country ? ", " + first.country : "");
    } else if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || (lat == null && lon == null)) {
      return res.status(400).json({ error: "city yoki lat/lon kerak." });
    }

    const url =
      `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}` +
      `&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m` +
      `&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max` +
      `&forecast_days=4&timezone=auto`;
    const wr = await fetch(url);
    if (!wr.ok) return res.status(502).json({ error: "Ob-havo xizmati javob bermadi." });
    const w = await wr.json();

    const data = JSON.stringify({ place: place || "foydalanuvchi turgan joy", current: w.current, daily: w.daily });
    const prompt =
      `Savol: ${String(question || "Ob-havo qanday?").slice(0, 500)}\n` +
      `Hozirgi vaqt: ${String(now || "").slice(0, 80)}\n` +
      `Ob-havo ma'lumoti: ${data}`;
    const reply = await gemini({
      system: WEATHER_PROMPT,
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      maxOutputTokens: 400,
      temperature: 0.3,
    });
    res.json({ reply: reply || "Ob-havoni aniqlay olmadim." });
  } catch (e) {
    console.error(e);
    res.status(e.status ?? 500).json({ error: e.message || "Server xatosi." });
  }
});

// Agent: { goal, screen, history, app } -> { action, id?, text?, direction?, say? }
app.post("/agent", async (req, res) => {
  if (!authorized(req, res)) return;
  const { goal, screen, history, app: pkg } = req.body ?? {};
  if (typeof goal !== "string" || typeof screen !== "string") {
    return res.status(400).json({ error: "goal va screen kerak." });
  }
  const steps = Array.isArray(history) ? history.slice(-12).map(String) : [];
  const prompt =
    `Maqsad: ${goal.slice(0, 1000)}\n` +
    `Joriy ilova: ${String(pkg ?? "").slice(0, 200)}\n` +
    `Avvalgi qadamlar:\n${steps.length ? steps.join("\n") : "(yo'q)"}\n\n` +
    `Ekran:\n${screen.slice(0, 12000)}`;
  try {
    const raw = await gemini({
      system: AGENT_PROMPT,
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      json: true,
      maxOutputTokens: 512,
      temperature: 0.2,
    });
    const obj = parseJson(raw);
    if (!obj || typeof obj.action !== "string") {
      return res.json({ action: "wait" });
    }
    res.json(obj);
  } catch (e) {
    console.error(e);
    res.status(e.status ?? 500).json({ error: e.message || "Server xatosi." });
  }
});

app.listen(PORT, () => console.log(`Jarvis server ${PORT}-portda ishlayapti`));
