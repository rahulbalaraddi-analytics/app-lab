# IdeaDrop

Speak an idea anytime → it becomes text, saved with the date and time → export everything as one Markdown file for Claude to turn into an actionable checklist.

**Offline and private:** speech-to-text runs on the phone. Nothing leaves the device unless you export it.

## Features (v0.4)

| Feature | Status |
|---|---|
| One-tap voice recording (keeps going with the screen locked) | ✅ |
| Speech-to-text with the phone's built-in engine: English (India/US), Hindi, Kannada | ✅ |
| "Record idea" shortcut: side key, lock screen, home screen | ✅ |
| **Calls tab**: your call recordings listed in the app, tap **To text** (English, Kannada, Hindi or mixed). Also Share → IdeaDrop | ✅ |
| Notes with date, time, title, category, done/pending | ✅ |
| Add more to a note by voice or typing | ✅ |
| Play back original audio | ✅ |
| Export `.md`: All or Pending only (share or save) | ✅ |
| Audio cleanup after 30 days (always asks first) | ✅ |
| Smart AI titles + categories (offline) | Next |
| Widget, Quick Settings tile, car/bedtime auto-listen | M2 |
| Offline AI grouping + checklist | M3 |
| Google Drive backup | M4 |

See the full plan in [PRD.md](PRD.md).

## Install

**Option A — on the phone (no laptop):**
1. Open [releases/tag/ideadrop-latest](https://github.com/rahulbalaraddi-analytics/app-lab/releases/tag/ideadrop-latest)
2. Tap **IdeaDrop.apk** → open → allow "install unknown apps" → Install

**Option B — Android Studio:**
1. Clone the repo, then **File → Open** the `ideadrop` folder (not the repo root)
2. Wait for Gradle sync, plug in the phone (USB debugging on), press **Run ▶**

## First run

1. Tap the **mic**, allow microphone, speak, tap **Stop & save**
2. For offline speech, install your language pack in the phone's voice-typing settings
3. Optional: side key → Settings → Advanced features → Side button → Double press → Open app → **Record idea**

## Tech

Kotlin · Jetpack Compose · Room · Android SpeechRecognizer · Foreground service for recording
