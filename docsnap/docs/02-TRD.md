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
| Files | App files dir `/<folder>/` | Survives updates |
| Upload | Google Drive REST API v3 + WorkManager | Resumable, retry, Wi-Fi / charging constraints |
| Sign-in | Credential Manager (Google) + OAuth Drive scope | Each helper uses own Gmail |
| Min SDK | 26 (Android 8) | All family phones |
| Build | GitHub Actions → debug APK (same as IdeaDrop) | Share APK on WhatsApp/Drive |

## 3. Scanning

- `SCANNER_MODE_FULL`, gallery import on, page limit 50; Single-page mode → page limit 1.
- Request **PDF only** (each extra format costs processing time).
- After the scanner returns, DocSnap shows its own **Pages** screen: thumbnails, Add page (relaunches scanner and appends), reorder, delete, **Done – N pages**. Merging/appending done with Android `PdfRenderer`/`PdfDocument` or page JPEGs → PDF.
- First launch downloads scanner models via Play services — do it on Wi-Fi during setup.
- The camera screen itself is Google's; we cannot restyle it.

## 4. Voice naming

- Auto-start after Done; stop on 2 sec silence or 30 sec max.
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

- Folder list = sub-folders of root, refreshed on each app open; created only by owner (in app → also on Drive).
- File name carries person code + per-person serial → two phones never write the same name.
- Before upload: compute sha256, skip if already uploaded. Mark **On Drive** only after Drive returns a file id.
- CSV columns: `file_name, transcript, language, person, pages, scanned_at, folder, drive_link`. Rewritten (not appended remotely) from local DB after each upload batch.

## 6. Drive auth (highest risk)

| Option | Pros | Cons |
|---|---|---|
| **A.** Full `drive` scope, own Cloud project, unverified, ≤ 100 users | Sees the shared folder | "Unverified app" warning at login; Testing status may force weekly re-login — check |
| **B.** `drive.file` scope | No warning | May not see folders created by another account — must test |
| **C.** Share sheet to Drive app | No setup | Manual, one file at a time |

**Plan:** Day-1 spike on 2 phones with 2 Gmail accounts: try B, fall back to A. Record token expiry behaviour.

## 7. Permissions

Camera (ML Kit), Microphone, Internet, Post notifications (upload progress). No contacts, no location, no storage permission.

## 8. Non-functional

| Area | Requirement |
|---|---|
| Offline | Scan, name, save fully offline; uploads wait |
| Storage | ~300–500 KB per 1-page PDF → 2,500 docs ≈ 1 GB; fits free Drive |
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
