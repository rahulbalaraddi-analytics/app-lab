# DocSnap — Set up your Drive with Claude

**TL;DR:** Copy the prompt below into Claude (with Google Drive connected). Claude plans your folders, creates them in Drive and shares them. Then send the folder link to everyone → they paste it in DocSnap → start scanning.

## What DocSnap is (share this)

DocSnap is a free Android app to put **all your important papers on the phone and into one shared Google Drive**: property documents, court case history, college certificates, medical files, bills, life documents.

- **Single page:** photo → say the name → next paper. About 30 seconds each.
- **Multi-page:** take all pages → Done → say the name. Saved every 5 pages, so nothing is lost.
- **Best quality:** edge detection, crop, original colour, photos go into the PDF with no quality loss.
- **Voice names** in English or Kannada. A name is compulsory, so no paper is left unnamed.
- **Many phones, one Drive:** everyone pastes the same folder link and sees the same folders.
- **Works offline;** upload everything together on Wi-Fi. Notification when all are uploaded.
- **Built for Claude:** each folder gets an index file so Claude can clean names and build a register later.

## Step 1 — Prompt to give Claude

Connect Google Drive in Claude first, then paste this and fill the brackets:

```text
I'm digitising my family's paper documents with an Android app called DocSnap.
Several people scan on different phones; every scan goes into ONE shared Google Drive
folder that I own, with one sub-folder per category. The app reads the sub-folders of
that one link, so the folder structure must be set up before we start.

What we are scanning: [e.g. land and property papers, court case history, college
and school certificates, medical records, bank/insurance papers, IDs and life documents]
Roughly how many papers: [e.g. 2,000–2,500]
Who is scanning (Gmail addresses): [list emails]

Please:
1. Propose 5–10 top-level folders that cover these papers without overlap. For each give:
   a clear folder name, a 3–5 letter UPPERCASE code (used in file names, e.g. LAND),
   and one line on what goes in it. Keep names short and obvious for elderly users.
   Name each Drive folder as "<Name> (<CODE>)", e.g. "Land Properties (LAND)" — the app
   reads the code from the brackets and shows only the name.
2. After I approve, create in my Google Drive:
   - a root folder named "[Family Documents]"
   - each approved folder inside it
   - a hidden folder "_DocSnap_Analytics" inside the root (the app writes logs there)
   - in the root, a "README" doc listing every folder, its code and what goes in it
3. Share the root folder as Editor with the scanner emails above.
4. Give me the root folder link and a short WhatsApp message (no emojis, no asterisks)
   telling everyone to install DocSnap and paste that link on first launch.
Don't create sub-sub-folders; one level only.
```

## Step 2 — In the app

1. Install the DocSnap APK.
2. First launch: watch the short tutorial → sign in with Google → **paste the root folder link**.
3. The app shows every folder in it. **Refresh** picks up folders added later in Drive.
4. Pick a folder → Single page or Multi-page → scan → say the name.
5. On Wi-Fi → **Upload all**.

## Step 3 — Later, with Claude

Ask Claude to read the root folder: it reads each folder's `_index_<initials>.csv` (file name + what was said) and `_DocSnap_Analytics` (phone quality, problems), then fixes names, flags low-quality scans and builds a register.

