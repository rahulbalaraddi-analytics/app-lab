# IdeaDrop — Product Requirements Document

| Field | Value |
|---|---|
| **Version** | 0.4 |
| **Status** | v0.3 app built (audio import); AI titles next |
| **Platform** | Android (Kotlin + Jetpack Compose) |
| **Last updated** | 2026-10-10 |

---

## 1. Problem

Ideas show up at odd moments — falling asleep, driving, cooking, walking. Typing is impossible or unsafe, and the idea is gone in minutes.

## 2. Solution

A hands-free voice inbox: **speak → auto-transcribed → saved as a timestamped note → grouped by project → turned into a checklist**. Notes can be exported as one Markdown file for deeper planning in an AI assistant (e.g. Claude).

## 3. Goals & Non-Goals

| Goals | Non-Goals (for now) |
|---|---|
| Capture an idea in **≤ 2 seconds**, often with zero touch | Always-on listening |
| Work **fully offline**, private by default | Paid cloud services / API keys |
| Turn messy speech into a usable checklist | iOS / web versions |
| Easy export to an external AI | Direct write into an AI's memory |

## 4. Users

| Phase | User |
|---|---|
| v1 | Author (personal use, English) |
| Later | Public via Play Store; family users speaking Kannada / Hindi |

---

## 5. Features & Priority

**P0 = MVP · P1 = next · P2 = later · Future = idea backlog**

### 5.1 Capture

| Feature | Priority | Notes |
|---|---|---|
| Record button in app | P0 | One tap start/stop |
| Home-screen widget | P0 | One tap to record |
| Lock-screen shortcut | P0 | "Record idea" app entry, usable as a lock-screen shortcut ✅ v0.2 |
| Quick Settings tile | P0 | Pull-down panel button |
| Side-key double-press | P0 | Samsung setting → Open app → **Record idea** → starts recording ✅ v0.2 |
| "Hey Google, open IdeaDrop" | P0 | Deep link auto-starts recording |
| Smart auto-listen windows | P0 | Only when connected to car Bluetooth, or bedtime window (default **10 PM – 1 AM**, changeable); manual on/off toggle |
| Auto-stop on silence | P0 | Saves automatically — driving mode needs zero touch |
| Custom "Hey Idea" wake word | P1 | Offline open-source wake-word engine, toggle-only |
| Volume long-press trigger | P1 | Personal build only (Accessibility API is a Play Store policy risk) |
| Headset button | Future | |

### 5.2 Transcription

| Feature | Priority | Notes |
|---|---|---|
| Speech-to-text | P0 | **Phone's built-in engine** (Google/Samsung), English (India) default; offline with the phone's language pack. Vosk dropped after test (~48% accuracy) |
| Keep original audio | P0 | Streamed to the engine on Android 13+ so audio is kept; text-only fallback if the phone's engine doesn't support it |
| Audio cleanup after N days | P0 | Default **30 days**, changeable (7/15/30/60/90/Never). **Always asks before deleting**; text is kept |
| Free cloud transcription when online | P2 | Only if a free, keyless option exists |
| Kannada, Hindi | P0 | Language choice in Settings (needs phone language pack). Hinglish: Future |
| "My words" list | P0 | Names/terms hinted to the engine (Android 13+) |

### 5.2b Import audio (call recordings, voice notes)

| Feature | Priority | Notes |
|---|---|---|
| Import any audio file (m4a, mp3, amr, ogg, wav) → text note | P0 | ✅ v0.3. Import button on Home, or **Share → IdeaDrop** from Files / call recordings |
| Languages: English, Kannada, Hindi, or **Mixed** | P0 | Mixed = engine switches language mid-sentence (Android 14+, phone engine must support it) |
| Runs on the phone, in the background | P0 | Foreground service; ~as long as the audio; cancel anytime |
| Original audio kept with the note, category "Calls" | P0 | Title from file name (Samsung names include the contact) |
| Who-said-what (speaker labels), summary | Future | Not possible with the phone engine; needs a cloud AI (P2, optional free key) |

**Known limits:** call audio is low quality (phone-line sound, two people talking over each other), so accuracy will be lower than speaking into the app. Mixed-language accuracy depends on the phone's speech engine.

### 5.3 Notes

| Feature | Priority | Notes |
|---|---|---|
| Each recording = one note (date, time, text, audio) | P0 | |
| Edit title and text | P0 | |
| Append to a note by voice or typing | P0 | "Speak again under the same bucket" |
| Set / change category | P0 | |
| Search notes | P1 | |

### 5.4 Grouping & Checklist (on-device AI)

| Feature | Priority | Notes |
|---|---|---|
| Smart title (clean, readable) | P0 | v0.2: rule-based (drops "I want to…" etc.). Next: on-device AI |
| Offline LLM groups notes into categories | P1 | Small model (~0.5–1B), optional download |
| LLM builds a quick checklist per category | P1 | Short, actionable items |
| Edit categories by voice or text ("my categories are…") | P1 | |
| Done / pending tracking per note | P0 | Per checklist item in P1 |
| Detailed plan via external AI | P0 | Through export (5.5) |

> **Why hybrid:** a ~1B on-device model groups ideas and drafts short checklists well, but detailed planning is better done by a large model after export.

### 5.5 Export & Sync

| Feature | Priority | Notes |
|---|---|---|
| Export notes as one `.md` / `.txt` file | P0 | Android share sheet |
| Export scope: **All** (default) or **Pending only** | P0 | |
| Local backup file on phone | P0 | |
| Auto-sync to a Google Drive folder | P1 | User signs in with own Google account |
| Push checklist to Google Tasks / Keep | Future | |

---

## 6. Non-Functional Requirements

| Area | Requirement |
|---|---|
| **Privacy** | All audio/text stays on device unless the user exports or turns on Drive sync. No analytics in v1. |
| **Battery** | No always-on mic. Listening only in user-set windows, with a visible notification (Android rule). |
| **Offline** | Capture, transcription, notes and grouping work with no internet. |
| **Storage** | Models downloaded on demand; audio cleanup setting. |
| **Target device** | Android 10+; tested on Samsung Galaxy S21 FE (8 GB RAM). |
| **Open source** | Public repo, MIT license, no secrets or personal data committed. |

## 7. Tech Stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Storage | Room (SQLite) + app files for audio |
| Background | Foreground service for recording; WorkManager for jobs |
| Speech-to-text | Android SpeechRecognizer (phone's engine), Whisper as backup option |
| On-device LLM | MediaPipe LLM Inference + small Gemma model |
| Widget | Jetpack Glance |
| Drive sync | Google Drive REST API (user's own account) |

## 8. Milestones

| # | Milestone | Scope | Tag |
|---|---|---|---|
| **M1** | Core capture | Record (keeps going on lock screen) → offline transcribe → timestamped notes → edit, category, done/pending, add by voice → export `.md` (All / Pending) → audio cleanup prompt | `ideadrop-v0.1` |
| **M2** | Hands-free | Widget, Quick Settings tile, lock-screen shortcut, deep link, auto-stop on silence, car/bedtime windows | `ideadrop-v0.2` |
| **M3** | AI grouping | On-device LLM categories + quick checklist + done/pending | `ideadrop-v0.3` |
| **M4** | Sync | Google Drive folder backup | `ideadrop-v0.4` |
| **M5** | Polish | Icons, theming, onboarding, Play Store readiness | `ideadrop-v1.0` |

## 9. Open Questions

| # | Question | Status |
|---|---|---|
| 1 | Default audio retention period | ✅ 30 days, ask before delete, changeable |
| 2 | Bedtime window default hours | ✅ 10 PM – 1 AM, changeable |
| 3 | Free keyless cloud STT option exists? | Research in P2 |

## 10. Changelog

| Date | Version | Change |
|---|---|---|
| 2026-10-10 | 0.1 | First draft from requirements discussion |
| 2026-10-10 | 0.1.1 | Sync test: confirmed Claude → GitHub push works |
| 2026-10-10 | 0.2 | Audio cleanup (30 days, ask first), bedtime 10 PM–1 AM, Vosk for M1 speech, M1 scope expanded |
| 2026-10-10 | 0.3 | Accuracy test: Vosk ~48% vs phone engine. Switched to phone's built-in engine (en-IN, Hindi, Kannada), My words, Record idea shortcut (side key / lock screen), better auto titles |
| 2026-10-30 | 0.4 | Import audio files (call recordings) → text, English/Kannada/Hindi/Mixed, Share → IdeaDrop; Mixed language option for recording; phone download link (ideadrop-latest) |
