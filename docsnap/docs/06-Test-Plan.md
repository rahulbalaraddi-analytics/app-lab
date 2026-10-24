# DocSnap — Test plan

**TL;DR:** Prove Drive auth first, then speed (≤ 30 sec/doc) on the oldest phone, then 2 phones uploading together with no clashes.

| # | Test | Phones | Pass when |
|---|---|---|---|
| 0 | **Spike:** helper account uploads into owner's shared folder | 2 | File appears in owner's Drive; note login warning + token expiry |
| 0b | **Quality spike:** ML Kit page resolution + file size | S21 FE + oldest | ≥ 8 MP per A4 page; else switch to CameraX + OpenCV |
| 0c | JPEG → PDF loss check | any | Image extracted from PDF is byte-identical to the captured JPEG |
| 1 | Single page, 20 papers in a row | oldest | ≤ 30 sec each, photo → name → next |
| 2 | Multi-page, 12 pages | any | One 12-page PDF, pages in order |
| 3 | Swipe page 7 up | any | Page gone, Undo works, PDF has 11 pages |
| 3b | Kill app at page 23 of multi-page | any | Reopen shows 20 saved pages; Continue works |
| 4 | Try to leave naming screen without a name | any | Impossible: back/next blocked until named |
| 4b | Speech engine offline / fails | any | Type fallback works; doc still named |
| 4c | Paste folder link on a new phone, Refresh after owner adds a folder in Drive | 2 | Same folder list on both phones |
| 5 | Kannada name | S21 FE + oldest | Transcript in Kannada in CSV; Latin slug in file name |
| 6 | Airplane mode, 10 docs | any | All saved; upload starts when back online |
| 7 | Kill app mid-upload | any | Resumes; no duplicates on Drive |
| 8 | Two phones, same folder, 50 docs each | 2 | 100 files, 2 CSVs, zero name clashes |
| 9 | Same paper scanned twice by same person | any | Second upload skipped or flagged |
| 10 | Owner adds folder | owner + helper | Helper sees it after reopening the app |
| 11 | Pilot: 100 real docs by family | 4–5 | Helpers finish without help |
