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

const app = express();
app.set("trust proxy", 1);
app.use(cors());
app.use(express.json({ limit: "100kb" }));

// Har bir IP uchun daqiqasiga 20 ta so'rov — bepul limitni himoya qiladi
app.use(
  "/chat",
  rateLimit({
    windowMs: 60 * 1000,
    limit: 20,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: "Juda ko'p so'rov. Biroz kutib turing." },
  })
);

app.get("/health", (_req, res) => res.json({ ok: true }));

app.post("/chat", async (req, res) => {
  if (APP_KEY && req.get("x-app-key") !== APP_KEY) {
    return res.status(401).json({ error: "Ruxsat yo'q." });
  }

  const { messages } = req.body ?? {};
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: "messages massivi kerak." });
  }

  const contents = messages
    .slice(-20)
    .filter((m) => m && typeof m.content === "string" && m.content.trim())
    .map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content.slice(0, 4000) }],
    }));

  if (contents.length === 0 || contents[contents.length - 1].role !== "user") {
    return res.status(400).json({ error: "Oxirgi xabar foydalanuvchidan bo'lishi kerak." });
  }

  try {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
    const r = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": GEMINI_API_KEY,
      },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents,
        generationConfig: { temperature: 0.7, maxOutputTokens: 2048 },
      }),
    });

    const data = await r.json();
    if (!r.ok) {
      console.error("Gemini xatosi:", r.status, JSON.stringify(data));
      const msg =
        r.status === 429
          ? "Bepul limit tugadi, birozdan keyin urinib ko'ring."
          : "AI xizmatida xatolik.";
      return res.status(502).json({ error: msg });
    }

    const reply = (data.candidates?.[0]?.content?.parts ?? [])
      .map((p) => p.text ?? "")
      .join("")
      .trim();

    res.json({ reply: reply || "Javob olinmadi, qayta urinib ko'ring." });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Server xatosi." });
  }
});

app.listen(PORT, () => console.log(`Jarvis server ${PORT}-portda ishlayapti`));
