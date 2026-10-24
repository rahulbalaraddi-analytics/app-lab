# DocSnap — Flowcharts

**TL;DR:** One loop per document (scan → Done → name → save → queue); uploads run separately in the background.

## 1. Scan a document

```mermaid
flowchart TD
  A[Pick folder] --> B[New Document]
  B --> C{Single-page mode?}
  C -- yes --> D[Take 1 photo] --> F
  C -- no --> E[Take photos: Page 1, 2, 3...] --> E2{More pages?}
  E2 -- yes --> E
  E2 -- no --> F[Done - N pages]
  F --> G[Join pages into one PDF]
  G --> H[Mic opens: speak name]
  H --> I{Text OK?}
  I -- Re-record --> H
  I -- Edit --> J[Fix text]
  I -- untouched 3 sec / Save --> K
  J --> K[Save PDF + DB row + CSV row]
  K --> L[Add to upload queue]
  L --> B
```

## 2. Upload worker

```mermaid
flowchart TD
  A[Queue has items] --> B{Online? Wi-Fi rule met?}
  B -- no --> W[Wait for network] --> B
  B -- yes --> C[sha256 already on Drive?]
  C -- yes --> D[Mark On Drive]
  C -- no --> E[Resumable upload to Drive folder]
  E --> F{Drive returned file id?}
  F -- yes --> D --> G[Rewrite _index_PERSON.csv]
  F -- no --> H[Mark Failed, back off, retry]
  H --> B
```

## 3. Owner setup (once)

```mermaid
flowchart LR
  A[Sign in with Google] --> B[Create or pick Family Documents]
  B --> C[Share Editor with helpers]
  C --> D[Add folders: name + code]
  D --> E[Helpers install APK, sign in, set initials]
```
