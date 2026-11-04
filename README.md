# Jarvis

Android AI yordamchi (chat + ovozli buyruqlar) va unga bepul AI beradigan server.

```
jarvis/
├── server/   Node.js server (Gemini bepul API ga ulanadi, kalit shu yerda saqlanadi)
└── app/      Android Studio loyihasi (Kotlin + Jetpack Compose)
```

## 1. Bepul API kalit
https://aistudio.google.com/apikey saytida Google hisobingiz bilan kirib, bepul **API key** oling.

## 2. Serverni ishga tushirish
```bash
cd server
npm install
GEMINI_API_KEY=sizning_kalit npm start     # Windows PowerShell: $env:GEMINI_API_KEY="..."; npm start
```
Tekshirish: `http://localhost:3000/health` → `{"ok":true}`

## 3. Serverni internetga chiqarish (hamma foydalanishi uchun)
Bepul variantlardan biri — **Render.com**:
1. `server/` papkasini GitHub'ga yuklang.
2. Render → New → Web Service → shu repo. Build: `npm install`, Start: `npm start`.
3. Environment'ga `GEMINI_API_KEY` (va xohlasangiz `APP_KEY`) qo'shing.
4. Berilgan manzilni oling, masalan `https://jarvis-xxxx.onrender.com`.

Eslatma: Render bepul rejasida server 15 daqiqa ishlatilmasa uxlaydi, birinchi javob ~30 soniya kechikishi mumkin.

## 4. Ilovani sozlash va APK yig'ish
1. `app/` papkasini Android Studio'da oching (Gradle wrapper o'zi yaratiladi).
2. `app/app/build.gradle.kts` ichida `SERVER_URL` ni o'z manzilingizga almashtiring (`APP_KEY` ni ham serverdagi bilan bir xil qiling, agar ishlatgan bo'lsangiz).
3. Telefonda sinash: Run ▶. APK olish: **Build → Build APK(s)**.
4. APK faylni do'stlarga yuboring — ularning hammasi bitta server orqali ishlaydi.

## Ovozli buyruqlar
Mikrofon tugmasini bosib ayting: "Telegramni och", "YouTube och", "open Instagram", "Sozlamalarni och".
Buyruq ilova ochish bo'lsa — telefon o'zi bajaradi, aks holda savol AI ga ketadi va javob ovoz bilan o'qiladi.
Til `VoiceController.kt` ichida `languageTag` bilan o'zgartiriladi (`uz-UZ`, `ru-RU`, `en-US`).
