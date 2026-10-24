# DocSnap — TRD (Technical Requirements)

| Field | Value |
|---|---|
| Version | v0.1 (plan) |
| Updated | 24 Oct 2026 |

**TL;DR:** Kotlin + Compose · ML Kit scanner · phone speech engine (reuse IdeaDrop's `PhoneSpeech`) · Room · Drive REST API + WorkManager. Per-person names and CSVs prevent clashes. **Drive auth is the one real risk — spike it on Day 1.**

## 1. Architecture

Local-first: everything is saved on the phone first; a background worker uploads to one shared Drive folder. Claude reads that folder later.

```
Phone (×5)                                   Google Drive                Claude
Scan ─► Pages ─► Done ─► Speak name ─► Save ─► Upload queue ─► Family Documents/<folder>/ ─► reads PDFs + CSVs
        (ML Kit)          (speech)     (PDF+DB)  (WorkManager)
```

## 2. Stack

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | Same as other app-lab apps |
| Scanner | ML Kit Document Scanner `play-services-mlkit-document-scanner` | Free, on-device, ready scan UI, multi-page, PDF out, ~300 KB app size |
| Speech | Phone engine via `SpeechRecognizer`, en-IN / kn-IN — **reuse `ideadrop/.../speech/PhoneSpeech.kt`** | Free, offline with language pack, keeps audio on Android 13+ |
| Local DB | Room | Docs + upload state |
| PDF | PdfBox-Android (Apache 2.0) | Embeds JPEGs without re-compression |
| Fallback capture | CameraX + OpenCV | Only if ML Kit resolution is too low |
| Files | App files dir `/<folder>/` | Survives updates |
| Upload | Google Drive REST API v3 + WorkManager | Resumable, retry, Wi-Fi / charging constraints |
| Sign-in | Credential Manager (Google) + OAuth Drive scope | Each helper uses own Gmail |
| Min SDK | 26 (Android 8) | All family phones |
| Build | GitHub Actions → debug APK (same as IdeaDrop) | Share APK on WhatsApp/Drive |

## 3. Scanning

| Topic | How |
|---|---|
| Mode | User picks **Single page** (scanner `pageLimit = 1`, loop straight to naming) or **Multi-page** |
| Multi-page checkpoints | Scanner launched in **batches of 5** (`pageLimit = 5`). Each batch's JPEGs are copied into a draft folder + DB rows **before** the next batch opens. Crash → at most 4 pages lost |
| Recovery | On app start, any draft without a name → "Unfinished document: N pages — Continue / Discard" |
| Page strip | Our Compose screen: horizontal thumbnails, swipe-up = delete (with Undo snackbar), tap = re-crop, long-press = reorder |
| Edges & crop | ML Kit `SCANNER_MODE_FULL`: live edge outline, auto-capture, 4-corner crop, rotate, perspective fix |
| Colour | Default **Original**; auto-enhance off by default (protects stamps, ink signatures) |
| Output requested | **JPEG pages only** (not ML Kit's PDF) so we control the PDF |
| JPEG → PDF | **PdfBox-Android** `JPEGFactory.createFromStream` embeds the JPEG bytes unchanged (DCT pass-through) → **zero re-compression**. Page size set from image pixels at 300 DPI |
| Resolution | **ML Kit does not expose resolution or JPEG quality.** Day-1 spike measures MP + file size on S21 FE and the oldest phone |
| Fallback | If below ~8 MP/page: own capture with **CameraX** (max resolution, JPEG 95) + **OpenCV** edge detect / warp + our crop UI. Adds ~3–4 days |
| First launch | Scanner models download via Play services — do it on Wi-Fi during setup |

- The camera and crop screens in ML Kit mode are Google's; we cannot restyle them (CameraX fallback is fully ours).

## 4. Voice naming

- Auto-start after Done (multi-page) or after crop (single page); stop on 2 sec silence or 30 sec max.
- **Name lock:** Save & next is disabled until transcript/typed text is non-empty and the PDF + DB row are written. No skip path exists in the UI; back button returns to the naming screen while a named-less doc exists.
- Language toggle EN / ಕನ್ನಡ; default from Settings.
- Audio mode where supported (Android 13+), else mic mode (text only).
- Slug: lower-case → transliterate Kannada to Latin (ICU `Transliterator` "Kannada-Latin; Latin-ASCII") → non-alphanumerics to `-` → max 60 chars.
- Audio `.m4a` kept on phone only; deleted 30 days after upload.

## 5. Drive layout & multi-phone sync

```
Family Documents/              ← owner's root, helpers = Editor
  Land Properties/
    LAND_SB_0001_....pdf
    LAND_RB_0001_....pdf
    _index_SB.csv              ← one CSV per person = no edit clashes
    _index_RB.csv
  <Folder 2>/ ...
```

- **Connect:** first launch = Google sign-in + paste root folder link → parse folder id (`/folders/<id>`) → check write access → save in settings.
- **Folder list** = `files.list` of sub-folders under that id; refreshed on **Refresh** tap and on app open when online; cached for offline use. Owner creates folders in Drive, not in the app.
- File name carries person code + per-person serial → two phones never write the same name.
- Before upload: compute sha256, skip if already uploaded. Mark **On Drive** only after Drive returns a file id.
- CSV columns: `file_name, transcript, language, person, pages, scanned_at, folder, drive_link`. Rewritten (not appended remotely) from local DB after each upload batch.

## 6. Drive auth (highest risk)

| Option | Pros | Cons |
|---|---|---|
| **A.** Full `drive` scope, own Cloud project, unverified, ≤ 100 users | Sees the shared folder | "Unverified app" warning at login; Testing status may force weekly re-login — check |
| **B.** `drive.file` scope | No warning | May not see folders created by another account — must test |
| **C.** Share sheet to Drive app | No setup | Manual, one file at a time |

**Decided: A.** Pasting a folder link needs full `drive` scope — `drive.file` cannot open a folder the app didn't create. Cost: one-time "unverified app" warning at sign-in per helper. Day-1 spike confirms no forced weekly re-login.

## 7. Permissions

Camera (ML Kit), Microphone, Internet, Post notifications (upload progress). No contacts, no location, no storage permission.

## 8. Non-functional

| Area | Requirement |
|---|---|
| Offline | Scan, name, save fully offline; uploads wait |
| Storage | HD ≈ 2–4 MB per page → ~3,500 pages ≈ 8–12 GB. Free Drive (15 GB) is shared with Gmail/Photos → likely Google One 100 GB |
| No loss | Every page on disk within 5 pages; no doc can leave the naming screen unnamed |
| Reliability | PDF file + DB row written before the next document opens |
| Privacy | All processing on-device; Drive shared only with named family accounts |
| Performance | Test on the oldest ₹15–20k phone; Done → mic open in < 2 sec |
| Secrets | OAuth client id via `local.properties` / CI secret; never committed |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Drive login warning / re-login | Day-1 spike; 3-step login guide for helpers |
| Kannada transcription errors | Full transcript in CSV; Claude fixes names; Whisper in v2 if poor |
| Two people scan the same doc | sha256 skips exact copies; Claude flags near-copies |
| Wrong folder chosen | Folder name banner on every scan screen; move before upload |
| Forgot to tap Done between docs | Page count on Done button; Split (v2) |

## 10. Local-first uploads

- Folder list cached in Room; PDFs always written locally first into `<folder>/`.
- Upload mode (Settings): **Manual (default)** — *Upload all* sends everything together; or **Auto on Wi-Fi** (WorkManager, unmetered constraint).
- Upload is a background worker: the UI never blocks on Drive.
- Folders are created only in Drive; app reads them on Refresh. Folders starting with `_` are hidden.

## 11. Analytics & quality log

Written locally, then uploaded with the next upload batch to `Family Documents/_DocSnap_Analytics/`:

| File | Contents |
|---|---|
| `device_<initials>_<model>.json` | Model, manufacturer, Android/SDK, app version, back-camera max resolution (Camera2 `SCALER_STREAM_CONFIGURATION_MAP`), free storage, speech language packs |
| `docs_<initials>.csv` | file_name, folder, mode, pages, per-page width×height, MP, est. DPI (long side ÷ 11.69 in), file bytes, seconds taken, retakes, pages deleted, name source (speech/typed), upload attempts, status |
| `events_<initials>.csv` | timestamp, type (crash_recovered, draft_discarded, upload_failed, refresh, speech_failed, low_quality_page), detail |
| `feedback_<initials>.csv` | timestamp, typed/spoken complaint |

- Quality flag per page: **GOOD ≥ 8 MP · OK 5–8 MP · LOW < 5 MP**; LOW shows "Low quality — retake closer" immediately.
- No images or document text in logs — file names only.

## 12. Storage note

- A scanned PDF **is** its page images. We store **only the PDF** (images inside it), not separate image files. HD size comes from those embedded JPEGs.
