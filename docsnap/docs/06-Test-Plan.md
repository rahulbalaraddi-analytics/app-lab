# DocSnap — Test plan

**TL;DR:** Prove Drive auth first, then speed (≤ 30 sec/doc) on the oldest phone, then 2 phones uploading together with no clashes.

| # | Test | Phones | Pass when |
|---|---|---|---|
| 0 | **Spike:** helper account uploads into owner's shared folder | 2 | File appears in owner's Drive; note login warning + token expiry |
| 1 | 1-page doc end to end | oldest | ≤ 30 sec from New Document to next document |
| 2 | 8-page doc | any | One 8-page PDF, pages in order |
| 3 | Add page after save (before upload) | any | Same file, N+1 pages |
| 4 | Single-page mode, 20 docs in a row | any | 20 PDFs, no extra taps |
| 5 | Kannada name | S21 FE + oldest | Transcript in Kannada in CSV; Latin slug in file name |
| 6 | Airplane mode, 10 docs | any | All saved; upload starts when back online |
| 7 | Kill app mid-upload | any | Resumes; no duplicates on Drive |
| 8 | Two phones, same folder, 50 docs each | 2 | 100 files, 2 CSVs, zero name clashes |
| 9 | Same paper scanned twice by same person | any | Second upload skipped or flagged |
| 10 | Owner adds folder | owner + helper | Helper sees it after reopening the app |
| 11 | Pilot: 100 real docs by family | 4–5 | Helpers finish without help |
