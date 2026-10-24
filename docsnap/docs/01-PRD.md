# DocSnap — PRD (Product Requirements)

| Field | Value |
|---|---|
| App | DocSnap (Android) |
| Version | v0.1 (plan) |
| Owner | rahulbalaraddi-analytics |
| Status | PRD + TRD + design ready for review; build not started |
| Updated | 24 Oct 2026 |

**TL;DR:** New Document → pages → Done → speak name → one PDF in a folder → uploads to one shared Drive. Zero typing, ~30 sec per document, 4–5 phones, ₹0.

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
| Cost | ₹0 (free APIs, free Drive 15 GB) |
| Claude-ready | Every Drive folder has index CSVs |

## 3. Users

| User | Need |
|---|---|
| Owner | Creates folders + shared Drive at the start, reviews progress |
| Helpers (3–4 family, all have Gmail) | Open app, pick folder, scan, speak. Nothing else |
| Claude (later) | Reads Drive folder + CSVs, corrects names, builds a register |

## 4. Core flow

1. Open app → pick folder (e.g. **Land Properties**).
2. Tap **New Document**.
3. Take photos one by one — counter shows **Page 1, 2, 3…**; each page auto-crops; retake, delete or reorder.
4. Tap **Done** → all pages become **one PDF** (1 page or 15, same flow).
5. Mic **starts automatically** → speak name + any note → stops after 2 sec silence.
6. Text + file name shown for 3 sec with **Save / Re-record / Edit** → auto-saves if untouched.
7. PDF joins the **upload queue** → uploads by itself when online (Wi-Fi-only optional).
8. Back to **New Document** for the next one.

### Single page vs multi-page

| Case | User does | Result |
|---|---|---|
| 1-page document | New Document → 1 photo → Done → speak | 1-page PDF |
| Multi-page document | New Document → photos to last page → Done → speak | 1 multi-page PDF |
| Forgot a page | Open saved doc → **Add page** (before upload) | Same PDF, page added |
| Two docs merged by mistake | Open doc → **Split at page X** (v2) | Two PDFs |
| Stack of 1-page docs | **Single-page mode**: auto-Done after 1 photo | ~2,000 fewer taps |

- **Done is the only thing that ends a document** — nothing is named or uploaded before it.
- Done button shows the count (**"Done – 3 pages"**) so a missed or extra page is obvious.

## 5. Features

| Feature | MVP | Later |
|---|---|---|
| Edge-detect scan, auto-crop, filters, multi-page | ✓ | |
| PDF output (JPEG option) | ✓ | |
| Single-page mode toggle | ✓ | |
| Voice naming → auto file name (English, Kannada) | ✓ | |
| Audio clip kept on phone only, 30 days | ✓ | |
| Folders created by owner, same on all phones | ✓ | |
| Auto-upload queue, Wi-Fi-only, retry | ✓ | |
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
- Folders are **created by the owner at the start**, before scanning. Each app folder = one Drive sub-folder.
- Only the owner adds/renames folders (avoids "Land", "Lands", "Land docs").

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
