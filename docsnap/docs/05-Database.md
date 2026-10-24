# DocSnap — Database (Room)

**TL;DR:** 3 tables + settings. The local DB is the source of truth for upload state; Drive is the source of truth for the folder list.

## Tables

| Table | Column | Type | Notes |
|---|---|---|---|
| **folder** | id | TEXT PK | = Drive folder id |
| | name | TEXT | "Land Properties" |
| | code | TEXT | "LAND", unique |
| | colour | INT | chip colour |
| | updated_at | INT | last sync from Drive |
| **document** | id | TEXT PK | UUID |
| | folder_id | TEXT FK → folder | |
| | serial | INT | per person per folder |
| | file_name | TEXT | `LAND_SB_0013_slug.pdf` |
| | transcript | TEXT | full spoken text |
| | language | TEXT | en-IN / kn-IN |
| | page_count | INT | |
| | local_path | TEXT | PDF on phone |
| | audio_path | TEXT NULL | deleted after 30 days |
| | sha256 | TEXT | dedupe before upload |
| | created_at | INT | epoch ms |
| **upload** | document_id | TEXT PK FK | |
| | status | TEXT | WAITING / UPLOADING / ON_DRIVE / FAILED |
| | drive_file_id | TEXT NULL | set only on success |
| | attempts | INT | |
| | last_error | TEXT NULL | plain-words message |
| | uploaded_at | INT NULL | |

## Settings (DataStore)

| Key | Example |
|---|---|
| person_code | SB |
| default_language | en-IN |
| single_page_mode | false |
| wifi_only | true |
| root_folder_id | Drive id of "Family Documents" |
| is_owner | true / false |

## Index CSV (on Drive, one per person per folder)

`file_name, transcript, language, person, pages, scanned_at, folder, drive_link`
