# DocSnap — PRD (Product Requirements)

| Field | Value |
|---|---|
| App | DocSnap (Android) |
| Version | v0.1 (plan) |
| Owner | rahulbalaraddi-analytics |
| Status | PRD + TRD + design ready for review; build not started |
| Updated | 24 Oct 2026 |

**TL;DR:** Paste Drive folder link once → pick folder → Single page (photo → name → next) or Multi-page (pages → Done → name) → one HD PDF → uploads to the shared Drive. Saved every 5 pages, no unnamed docs, ~30 sec per page-1 doc.

## 1. Problem

- **2,000–2,500 paper documents** (mostly 1 page, some multi-page): land / property and family records.
- CamScanner: limited formats, upload failures, manual renaming = slow.
- Typing names is the slowest step.

## 2. Goals & success metrics

| Goal | Target |
|---|---|
| Time per 1-page doc (scan + name) | ≤ 30 sec |
| Total effort, 2,500 docs | ~21 person-hours (~4 hrs each for 5 people) |
| Upload success | 100% — no silent failures, retries until done |
| Typing needed | Zero (voice only) |
| Cost | ₹0 app; Drive storage may need Google One 100 GB (HD ≈ 8–12 GB total) |
| Claude-ready | Every Drive folder has index CSVs |

## 3. Users

| User | Need |
|---|---|
| Owner | Creates folders + shared Drive at the start, reviews progress |
| Helpers (3–4 family, all have Gmail) | Open app, pick folder, scan, speak. Nothing else |
| Claude (later) | Reads Drive folder + CSVs, corrects names, builds a register |

## 4. Core flow

1. **First launch (once):** sign in with Google → paste the shared Drive folder link → app loads every sub-folder.
2. Pick folder. **Refresh** pulls new/renamed folders from Drive (needs internet).
3. Choose **Single page** or **Multi-page**.
4. **Single page:** photo → check edges → speak name → saved → camera reopens for the next paper.
5. **Multi-page:** scan pages; thumbnails line up in a **strip at the bottom**; **swipe a page up to delete** (like WhatsApp); tap to re-crop. Pages **saved to the phone every 5 pages**.
6. **Done – N pages** → one PDF.
7. **Name is compulsory:** mic opens; Next is locked until a name is saved. Speech fails → re-record or type. No skip.
8. PDF joins the **upload queue** → uploads when online (Wi-Fi-only optional).

### Single page vs multi-page

| Case | User does | Result |
|---|---|---|
| 1-page documents (most) | Pick **Single page** once → photo → name → next | One PDF each, no Done tap |
| Multi-page document | Pick **Multi-page** → scan → Done → name | One multi-page PDF |
| Bad page in the middle | Swipe it up in the strip | Removed before the PDF is made |
| Crash / call at page 23 | Reopen → **Unfinished document: 20 pages — Continue** | At most 4 pages to rescan |
| Speech fails | Re-record or type; Next locked until named | No unnamed document ever |

- **No document is ever lost or left unnamed.**
- Unfinished documents are **not** uploaded to Drive — only finished, named PDFs go up (a half deed on Drive would confuse Claude).

## 5. Features

| Feature | MVP | Later |
|---|---|---|
| Edge-detect scan, auto-crop, filters, multi-page | ✓ | |
| PDF output (JPEG option) | ✓ | |
| Single page / Multi-page choice per folder visit | ✓ | |
| Bottom page strip, swipe up to delete, tap to re-crop | ✓ | |
| Save every 5 pages + recover unfinished document | ✓ | |
| Name lock (no skip) | ✓ | |
| Connect by Drive folder link + Refresh | ✓ | |
| Voice naming → auto file name (English, Kannada) | ✓ | |
| Audio clip kept on phone only, 30 days | ✓ | |
| Folders created by owner, same on all phones | ✓ | |
| Upload mode: Manual "Upload all" (default) or Auto on Wi-Fi; never blocks scanning | ✓ | |
| Analytics + quality log per phone to Drive `_DocSnap_Analytics` | ✓ | |
| Report a problem button | ✓ | |
| Status per file: waiting / on Drive / failed | ✓ | |
| Index CSV per person per folder | ✓ | |
| Import photos/PDFs from gallery | ✓ | |
| Today counter per person | ✓ | |
| Duplicate warning | | ✓ |
| On-device OCR text with each file | | ✓ |
| Split document at page X | | ✓ |
| Sub-folders | | ✓ |

## 6. Naming rules

- Format: `FOLDERCODE_PERSON_SERIAL_spoken-words.pdf`
- Example: `LAND_SB_0042_survey-123-bijapur-7-12-extract-uncles-share.pdf`
- Folder code 3–5 letters · person = 2-letter initials · serial = 4 digits per person per folder → no clashes across phones.
- Spoken words: lower-case, spaces → hyphens, symbols removed, max 60 chars. Kannada words transliterated to Latin for the file name; original kept in CSV.
- Full transcript always goes in the CSV.

## 7. Folder rules

- One shared root on Drive: **Family Documents**, owned by the owner's Google account; helpers get **Editor** access.
- Owner creates sub-folders **directly in Drive** at the start.
- Every phone connects by **pasting the root folder link**; the app shows all sub-folders; **Refresh** updates them. Same link = same folders on every phone.
- No folder creation inside the app — Drive is the single source of truth.

## 7a. Quality (lifetime archive)

- Best possible photo quality; **no quality loss** from photo to PDF (JPEG embedded as-is).
- Default colour = **Original** (auto-enhance can wash out stamps and ink signatures).
- Edges: blue outline on live camera, auto-capture when steady, 4 draggable corners, page straightened.
- Target ≥ 300 DPI at A4 (≈ 8–9 MP per page). Verified in the Day-1 spike.

## 8. Decisions (24 Oct 2026)

| Topic | Decision |
|---|---|
| Accounts | All helpers have Gmail; Editor access to shared root |
| Drive owner | The owner's "Claude 2026" Google account (connected to Claude) |
| Languages | English + Kannada |
| Audio | Phone only, auto-deleted 30 days after upload; transcript is what Claude uses |
| Phones | 2× S21 FE, iQOO (~2 yrs), 2× ₹15–20k phones (4–5 yrs) |
| Open | First 5–8 folder names + codes (owner, before pilot) |

## 9. Out of scope (MVP)

iPhone · Play Store listing · PDF editing · cloud OCR · use without a Google account.

## 10. Help & tutorial (self-serve, for 65+ users)

- **First launch:** short swipe tutorial: 3 steps, 5 photo tips with drawn YES / NO pictures, Single vs Multi-page, how to say a name, "nothing gets lost". Big text, one idea per screen, Skip allowed.
- **? button on every main screen** (Home, Folder, Multi-page, Name) opens tips for that screen; Folder and Multi-page help repeat the photo tips; every sheet links to the full tutorial.

| Photo tip | YES | NO |
|---|---|---|
| Background | Black cloth / black chart paper | Wood table, patterned bedsheet |
| Light | Window daylight, 2 side lamps | Flash, bright spot, own shadow |
| Angle | Phone flat above the paper | Phone tilted |
| Corners | All 4 inside, small gap | A corner cut off |
| Paper | Flat, folds pressed, hands away | Fingers holding, folds showing |

Recommended kit: an overhead phone stand (~₹500–800) + one black cloth.

## 11. Notifications

| When | Message |
|---|---|
| Upload batch finished | "5 of 5 uploaded — all safe in Drive › Land Properties" |
| Upload failed | "2 could not upload — will try again" / "open DocSnap to check" after many tries |
| Many waiting | "12 documents waiting — connect to Wi-Fi and tap Upload all" |
| Unfinished document | "7 pages saved but not named yet" |
| Low storage | "Phone storage almost full — upload and free space" |

## 12. Sharing & setup

[SETUP-PROMPT.md](../SETUP-PROMPT.md): what DocSnap is (shareable) + a ready prompt for Claude to plan folders, create them in Drive, share them and write the WhatsApp message.
