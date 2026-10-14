# app-lab

Android apps built from everyday ideas — offline-first, privacy-friendly, built with AI pair-programming.

## Apps

| App | What it does | Status |
|---|---|---|
| [**IdeaDrop**](ideadrop/) | Speak an idea anytime → offline transcription → timestamped notes → checklist | 🛠 In development |
| [**BatteryPilot**](batterypilot/) | Battery analytics on the phone → forecast to 11 PM → tick-box fixes → before/after | 🛠 In development |
| Swipe Cleaner | Clean photos & videos with swipe gestures | ⏳ Moving in |
| Nostalgica Games | Small, calm casual games | ⏳ Moving in |

## Structure

Each app is a standalone Android Studio project in its own folder:

```
app-lab/
├── ideadrop/
├── batterypilot/
├── swipe-cleaner/      (coming)
└── nostalgica-games/   (coming)
```

Open the app's folder (not the repo root) in Android Studio.

## Principles

- **Offline-first** — core features work without internet
- **Private by default** — your data stays on your device
- **No secrets in the repo** — signing keys and API keys are never committed

## License

[MIT](LICENSE)
