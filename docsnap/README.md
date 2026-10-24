# DocSnap

Scan family papers **fast**: New Document → take pages → **Done** → **say the name** → one PDF, uploaded to a shared Google Drive folder. Built to digitise ~2,000–2,500 documents with 4–5 people on different phones, then hand the Drive folder to Claude to clean names and build a register.

**Status:** 📐 Planning — PRD, TRD and screen designs ready for review. No code yet.

## What it does (MVP)

| Area | What you get |
|---|---|
| **Scan** | Google ML Kit scanner: edge detect, auto-crop, filters, multi-page — on the phone, free |
| **One document = one PDF** | Any number of pages until **Done**; optional Single-page mode (auto-Done after 1 photo) |
| **Voice naming** | Mic opens after Done; English or Kannada; text becomes the file name |
| **Folders** | Owner creates them at the start; same folders on every phone (read from Drive) |
| **Upload** | Queue uploads by itself when online, Wi-Fi-only option, retries, no clashes between phones |
| **Claude handoff** | Each Drive folder gets one index CSV per person: file name, full transcript, pages, who, when |

## Docs

| # | Doc |
|---|---|
| 1 | [PRD](docs/01-PRD.md) |
| 2 | [TRD](docs/02-TRD.md) |
| 3 | [Design](docs/03-Design.md) |
| 4 | [Flowcharts](docs/04-Flowcharts.md) |
| 5 | [Database](docs/05-Database.md) |
| 6 | [Test plan](docs/06-Test-Plan.md) |

## Next

1. Review the 6 screens (link in [Design](docs/03-Design.md)).
2. Owner decides first 5–8 folder names + short codes.
3. Day-1 spike: prove two Google accounts can upload into one shared folder.
