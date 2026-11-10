import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";

const {
  GEMINI_API_KEY,
  GEMINI_MODEL = "gemini-2.0-flash",
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

const COMMAND_PROMPT = `Sen Jarvis — Android telefondagi ovozli yordamchisan. Foydalanuvchi gapirgan matnni (ovozdan tanilgan, imlo xatosi bo'lishi mumkin; o'zbek, rus yoki ingliz tilida) tahlil qilib, FAQAT bitta JSON obyekt qaytar. Boshqa matn yozma.

Maydonlar:
- "action": quyidagilardan biri:
  "chat"      — oddiy savol/suhbat. "reply" ga javobni yoz (qisqa, foydalanuvchi tilida; ovozda o'qiladi, shuning uchun markdown ishlatma).
  "call"      — qo'ng'iroq qilish. "contact" (ism) yoki "number" (raqam).
  "sms"       — oddiy SMS yuborish. "contact" yoki "number", "message" (yuboriladigan matn). Foydalanuvchi ilova nomini aytmagan bo'lsa shuni ishlat.
  "open_app"  — ilovani ochish. "app" (ilova nomi).
  "app_task"  — aniq bir ilova ichida ish qilish (masalan Telegram/Plus/WhatsApp'da kimgadir xabar yozish). "app" (ilova nomi) va "goal" (bajariladigan ishning to'liq, aniq tavsifi; masalan: "Shahzoda ismli kontaktga kirib 'salom' deb yoz va yubor").
  "youtube"   — YouTube'da qidirish/ijro. "query", "play" (true bo'lsa birinchi videoni ochib ijro etadi).
  "alarm"     — budilnik. "hour" (0-23), "minute" (0-59), "label".
  "timer"     — taymer. "seconds", "label".
  "flashlight"— fonar. "on" (true/false).
  "navigate"  — xaritada yo'l/joy. "query".
  "agent"     — yuqoridagilarga to'g'ri kelmaydigan, lekin telefonda bajarsa bo'ladigan boshqa ish (sozlamalarni o'zgartirish, ilova ichida biror narsa qilish). "goal" ga to'liq tavsif yoz.
- "say": bajarishdan oldin ovozda aytiladigan juda qisqa tasdiq (foydalanuvchi tilida), masalan "Shahzodaga qo'ng'iroq qilyapman". "chat" uchun kerak emas.

Qoidalar: "Jarvis" so'zini e'tiborsiz qoldir. Ism lotin harfida, foydalanuvchi aytganidek yoz. Faqat JSON.`;

const AGENT_PROMPT = `Sen Android telefonni boshqaruvchi agentsan. Maqsadga erishish uchun ekrandagi elementlar ustida BITTA qadam tanla.

Ekran elementlari "[id] tur: matn" ko'rinishida beriladi. Bayroqlar: click=bosiladi, edit=matn kiritiladi, scroll=aylantiriladi, focused=fokusda.

FAQAT bitta JSON obyekt qaytar:
{"action": "click"|"type"|"scroll"|"back"|"home"|"open_app"|"wait"|"done"|"fail", "id": <element id>, "text": "<kiritiladigan matn>", "direction": "down"|"up", "say": "<done/fail bo'lganda foydalanuvchiga qisqa xabar>"}

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

app.get("/health", (_req, res) => res.json({ ok: true }));

function authorized(req, res) {
  if (APP_KEY && req.get("x-app-key") !== APP_KEY) {
    res.status(401).json({ error: "Ruxsat yo'q." });
    return false;
  }
  return true;
}

async function gemini({ system, contents, json = false, maxOutputTokens = 2048, temperature = 0.7 }) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
  const generationConfig = { temperature, maxOutputTokens };
  if (json) generationConfig.responseMimeType = "application/json";
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: system }] },
      contents,
      generationConfig,
    }),
  });
  const data = await r.json();
  if (!r.ok) {
    console.error("Gemini xatosi:", r.status, JSON.stringify(data));
    const err = new Error(
      r.status === 429 ? "Bepul limit tugadi, birozdan keyin urinib ko'ring." : "AI xizmatida xatolik."
    );
    err.status = 502;
    throw err;
  }
  return (data.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
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

// Ovozli buyruqni amalga aylantiradi: { text, history? } -> { action, ... }
app.post("/command", async (req, res) => {
  if (!authorized(req, res)) return;
  const { text, history } = req.body ?? {};
  if (typeof text !== "string" || !text.trim()) {
    return res.status(400).json({ error: "text kerak." });
  }
  const contents = Array.isArray(history) ? toContents(history) : [];
  // history oxirgi xabar sifatida text ni ham o'z ichiga olmasa, qo'shamiz
  if (contents.length === 0 || contents[contents.length - 1].role !== "user") {
    contents.push({ role: "user", parts: [{ text: text.slice(0, 2000) }] });
  }
  try {
    const raw = await gemini({
      system: COMMAND_PROMPT,
      contents,
      json: true,
      maxOutputTokens: 1024,
      temperature: 0.3,
    });
    const obj = parseJson(raw);
    if (!obj || typeof obj.action !== "string") {
      return res.json({ action: "chat", reply: raw || "Tushunmadim, qayta ayting." });
    }
    res.json(obj);
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
