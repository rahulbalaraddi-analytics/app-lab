# DocSnap — Design (UI / UX)

**TL;DR:** 7 screens (v2), big buttons, one teal action colour, amber for "listening / waiting". Built for parents and helpers who just want to scan and talk. Mockups: [DocSnap app screens](https://claude.ai/artifact/GQHHoVhS8rpCQWDp5UmbAc) (private canvas — share from its Share menu).

## 1. Principles

- **One big action per screen** (New Document, Done, Save, Upload all now) — 60–64 px tall, full width.
- **Always show where it's going:** folder name on every scan screen.
- **No typing** on the main path; Edit text is optional.
- Readable for older eyes: Atkinson Hyperlegible, body ≥ 15 px, labels ≥ 13 px, contrast ≥ 4.5:1.
- Touch targets ≥ 44 px.

## 2. Colours

| Token | Hex | Use |
|---|---|---|
| Paper | `#F7F6F2` | Background |
| Card | `#FFFFFF` / border `#E2E0D8` | Lists, panels |
| Ink | `#1B1F24` | Text |
| Quiet | `#5A6169` | Secondary text |
| Teal | `#0F6E6E` | Primary action, "On Drive" |
| Amber | `#A35512` | Listening, Waiting |
| Red | `#B42318` | Failed / Retry |

## 3. Screens

| # | Screen | Key elements |
|---|---|---|
| 0 | Connect (first launch) | Google sign-in ✓, paste Drive folder link, "Found: Family Documents · 10 folders", initials, language |
| 1 | Folders + Refresh | Scanned today, Waiting to upload, folders from Drive, **Refresh** button, last refreshed time |
| 2 | Pick mode | Teal "Scanning into" banner, two big buttons **Single page** / **Multi-page**, doc list with status |
| 3a | Single page: edges | Blue outline + 4 corner handles, Original / Grayscale / Rotate, Retake / Looks good |
| 3b | Multi-page | Live camera, "10 pages saved" badge, bottom page strip, swipe up to delete, Scan more / **Done – 12 pages** |
| 4 | Speak the name | EN / ಕನ್ನಡ, big amber mic, "You said", file-name preview, Re-record / Edit, **Save & next** with lock note |
| 5 | Upload queue | Progress bar, Waiting / Uploading / Failed, Wi-Fi-only, Upload all now |

## 4. Navigation

Connect (once) → Folders → Folder → Single page: camera → Edges → Name → camera again · Multi-page: camera + strip → Done → Name → Folder. Uploads and Connect open from Home.

## 5. Copy rules

- Plain words: "Waiting", "On Drive", "Retry" — no "sync", "pending", "error 403".
- Errors say what happens next ("No internet. Will retry by itself.").
