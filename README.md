# app-lab

Android apps built from everyday ideas — offline-first, privacy-friendly, built with AI pair-programming.

## Apps

| App | What it does | Status |
|---|---|---|
| [**IdeaDrop**](ideadrop/) | Speak an idea anytime → offline transcription → timestamped notes → checklist | 🛠 In development |
| [**BatteryPilot**](batterypilot/) | Battery analytics on the phone → forecast to 11 PM → tick-box fixes → before/after | 🛠 In development |
| [**Nidra**](nidra/) | Bedtime breathing, muscle relaxation and Indian story audio with a sleep timer | 🛠 In development |
| [**DocSnap**](docsnap/) | Scan family papers → pages → Done → speak the name → one PDF → shared Google Drive | 📐 Planning |
| Swipe Cleaner | Clean photos & videos with swipe gestures | ⏳ Moving in |
| Nostalgica Games | Small, calm casual games | ⏳ Moving in |

## Tools

| Tool | What it does |
|---|---|
| [**Wi-Fi Speed Diagnosis**](wifi-speed-diagnosis/) | Is your Wi-Fi slow? A free 2-minute read-only check for Mac. Tells you if it's your Wi-Fi, your apps, or your router/internet provider, in plain English. |

## Structure

Each app is a standalone Android Studio project in its own folder:

```
app-lab/
├── ideadrop/
├── batterypilot/
├── docsnap/
├── swipe-cleaner/      (coming)
└── nostalgica-games/   (coming)
```

Open the app's folder (not the repo root) in Android Studio.

## Docs for every app

Each app folder has a `docs/` with the same 6 files: **PRD · TRD · Design · Flowcharts · Database · Test plan**.

## Principles

- **Offline-first** — core features work without internet
- **Private by default** — your data stays on your device
- **No secrets in the repo** — signing keys and API keys are never committed

## License

[MIT](LICENSE)
