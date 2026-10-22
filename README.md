# Viva Coach

Practise your PhD viva out loud with an AI examiner that has read your thesis.

Upload your thesis, synopsis or paper. Viva Coach builds an examiner's dossier (chapters, methods, key numbers, assumptions, likely attack points), then runs a spoken mock examination: the examiner asks questions aloud, you answer by voice, and you get a score, what you missed and a stronger answer for each question, plus a final readiness report.

## Features

- **Reads your work**: PDF (up to 50 MB) or a single `.tex` / `.txt` file, digested once by Gemini into a dossier stored on the phone.
- **Four examiners**: Friendly guide, Strict external, Tough committee, Industry interviewer.
- **Four occasions**: PhD viva, synopsis seminar, conference Q&A, research job interview.
- **Focus**: whole work or a single chapter; 5, 10 or 15 main questions, with up to 2 follow-ups each.
- **Voice both ways**: on-device text to speech (Indian English when installed) and on-device speech recognition with long-answer support. Typing works too.
- **Coach mode**: feedback after every answer, or switch it off for a realistic exam with feedback only in the final report.
- **Reports**: overall score out of 100, strengths, weak areas and the exact questions to practise next. All history stays on the phone.

## Setup

1. Install the APK (from the GitHub Actions run, see below).
2. Get a free Gemini API key at <https://aistudio.google.com/apikey>.
3. Open the app, go to Settings, paste the key, tap **Check key and list models** and pick a model.
4. Add your thesis and start a round.

## Building

Every push to `main` builds the app on GitHub Actions. Open the **Actions** tab, pick the latest **Build APK** run and download the `viva-coach-apk` artifact. It contains a debug APK and a release APK (the release APK is signed with the debug key for testing only).

Locally: open the project in Android Studio, or run `./gradlew assembleDebug`.

Stack: Kotlin, Jetpack Compose, Material 3, OkHttp, kotlinx.serialization. Min Android 8.0 (API 26).

## Project layout

```
app/src/main/java/com/inventornext/vivacoach/
  AppViewModel.kt     navigation, import, examination flow
  MainActivity.kt
  ai/Gemini.kt        Gemini REST client (generateContent, File API, model list)
  ai/Prompts.kt       examiner personas, occasions and prompts
  data/               models, JSON file storage, settings
  voice/Speaker.kt    text to speech
  voice/Listener.kt   continuous speech recognition
  ui/                 Compose screens
```

## Before selling

- Users currently bring their own Gemini key. For a paid version, move the key to a small backend (for example a Cloudflare Worker) and add a Pro unlock.
- Replace the debug signing in `app/build.gradle.kts` with your own keystore before publishing on Google Play.
