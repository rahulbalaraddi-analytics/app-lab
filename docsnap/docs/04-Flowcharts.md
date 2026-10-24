# DocSnap — Flowcharts

**TL;DR:** Connect once by folder link. Then per document: Single page (photo → name → next) or Multi-page (batches of 5 saved → Done → name). Name is compulsory. Uploads run in the background.

## 1. First launch

```mermaid
flowchart LR
  A[Sign in with Google] --> B[Paste Drive folder link]
  B --> C{Folder found and writable?}
  C -- no --> B
  C -- yes --> D[Load all sub-folders]
  D --> E[Set initials + language]
  E --> F[Folders screen]
```

## 2. Scan

```mermaid
flowchart TD
  A[Pick folder] --> M{Single page or Multi-page?}
  M -- Single --> S1[Photo] --> S2[Check edges / crop] --> N
  M -- Multi --> P1[Scan batch of up to 5 pages]
  P1 --> P2[Save batch to phone: draft + DB]
  P2 --> P3[Strip: swipe up to delete, tap to re-crop]
  P3 --> P4{More pages?}
  P4 -- yes --> P1
  P4 -- no --> P5[Done - N pages: join JPEGs into one PDF, no re-compression] --> N
  N[Speak name] --> N2{Name text present?}
  N2 -- no / speech failed --> N3[Re-record or type] --> N2
  N2 -- yes --> K[Save PDF + DB row + CSV row]
  K --> L[Upload queue]
  L --> M2{Mode}
  M2 -- Single --> S1
  M2 -- Multi --> A
```

## 3. Crash recovery

```mermaid
flowchart LR
  A[App opens] --> B{Unnamed draft exists?}
  B -- no --> C[Folders screen]
  B -- yes --> D[Unfinished document: N pages]
  D -- Continue --> E[Back to strip or naming]
  D -- Discard, confirm twice --> C
```

## 4. Upload worker

```mermaid
flowchart TD
  A[Queue has named PDFs] --> B{Online? Wi-Fi rule met?}
  B -- no --> W[Wait] --> B
  B -- yes --> C{sha256 already on Drive?}
  C -- yes --> D[Mark On Drive]
  C -- no --> E[Resumable upload]
  E --> F{File id returned?}
  F -- yes --> D --> G[Rewrite _index_PERSON.csv]
  F -- no --> H[Mark Failed, back off] --> B
```

## 5. Refresh folders

```mermaid
flowchart LR
  A[Tap Refresh] --> B{Online?}
  B -- no --> C[Show cached folders + 'offline' note]
  B -- yes --> D[List sub-folders of linked folder] --> E[Update local list, same on every phone]
```
