# DocSnap — Design (UI / UX)

**TL;DR:** 6 screens, big buttons, one teal action colour, amber for "listening / waiting". Built for parents and helpers who just want to scan and talk. Mockups: [DocSnap app screens](https://claude.ai/artifact/GQHHoVhS8rpCQWDp5UmbAc) (private canvas — share from its Share menu).

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
| 1 | Folders (home) | Scanned today, Waiting to upload, folder cards (code chip + count), New Document |
| 2 | Inside a folder | Teal "Scanning into" banner, Single-page mode toggle, doc list with status, New Document |
| 3 | Pages, then Done | Page thumbnails, Add page, reorder/retake, **Done – 3 pages** |
| 4 | Speak the name | EN / ಕನ್ನಡ toggle, big amber mic, "You said", file-name preview, Re-record / Edit, auto-save countdown |
| 5 | Upload queue | Progress bar, queue with Waiting / Uploading / Failed, Wi-Fi-only, Upload all now |
| 6 | Setup (owner) | Google account, shared folder, initials, language, owner-only folder list + Add folder |

## 4. Navigation

Folders → Folder → (Google scanner camera) → Pages → Name → back to Folder (next doc). Uploads and Setup open from Home.

## 5. Copy rules

- Plain words: "Waiting", "On Drive", "Retry" — no "sync", "pending", "error 403".
- Errors say what happens next ("No internet. Will retry by itself.").
