# BatteryPilot — Design (UI / UX)

## 1. Principles

| Principle | In practice |
|---|---|
| **Answer first** | Top of Today = "Will I make 11 PM?" in one line, coloured |
| **Few words** | Short labels, numbers in big type, tables over paragraphs |
| **One action** | Tick → **Apply**. Everything else is optional |
| **Honest** | Every estimate is labelled as an estimate; "can't detect" stays "—" |

## 2. Colours

| Use | Colour | Meaning |
|---|---|---|
| Status good | `#1E8E3E` green | Makes target with buffer |
| Status warn | `#E37400` amber | Makes it, under buffer / caution |
| Status bad | `#D93025` red | Won't make it |
| Brand | `#1E6B3A` (light) / `#8FD8A0` (dark) | Plus Material You dynamic colours (Android 12+) |
| Charging line | `#34A853` | Chart only |

## 3. Navigation

| Area | Contents |
|---|---|
| Top bar | Title · ⟳ Refresh · ⚙ Setup |
| Bottom bar | **Today** · **Fixes** · **Apps** · **History** |
| Setup (⚙) | Full screen, back arrow; opens by itself on first launch |

## 4. Screens

### Today
```
┌──────────────────────────────────────┐
│ [Finish setup card — if needed]      │
├──────────────────────────────────────┤
│  62%   0% around 7:40 PM   (red)     │
│        3h 20m short of 11 PM         │
│        Range 0–4% · model: Medium    │
│ ┌───────────────┐┌─────────────────┐ │
│ │Draining now   ││Allowed till 11PM│ │
│ │8.2%/hr        ││5.1%/hr          │ │
│ └───────────────┘└─────────────────┘ │
├──────────────────────────────────────┤
│ Today  [chart: actual ━ forecast ╍   │
│         buffer ┄  now ┆  0% ●]       │
├──────────────────────────────────────┤
│ Key numbers  (2 × 4 tiles)           │
│ Off charger | Used since             │
│ Screen on   | Temperature            │
│ Screen-on   | Screen-off drain       │
│ Health      | Power saving           │
├──────────────────────────────────────┤
│ Where it's going  (bars)             │
├──────────────────────────────────────┤
│ Biggest wins (top 3) [Choose fixes]  │
└──────────────────────────────────────┘
```

### Fixes
```
┌──────────────────────────────────────┐
│ Tick what you want, then Apply.      │
│ You need ≈ +2h 10m to reach 11 PM    │
│ [Select recommended]                 │
├──────────────────────────────────────┤
│ ☐ Turn on Dark mode                  │
│   why… [+25m/day] [Opens settings]   │
│ ☐ Motion smoothness → 60 Hz          │
│   why… [+18m/day] [Auto]      Undo   │
│ …                                    │
│ Already done ✓ (greyed)              │
├══════════════════════════════════════┤
│ 3 selected  ≈ +1h 05m/day   [Apply]  │  ← sticky
└──────────────────────────────────────┘
```
- After **Apply**: Auto fixes done at once → snackbar. Guided fixes → card "Next: … [Open settings] [Stop]", one screen at a time.

### Apps
| Element | Detail |
|---|---|
| Header | "Exact since last full charge" or "Screen time (estimate) + Do USB setup" |
| Warning | "⚠ N apps drain mostly in the background" |
| Row | App name · bar · % · mAh · on-screen time · `Background heavy` chip |
| Tap | Opens the app's info page (Battery → Restricted) |

### History
| Card | Columns |
|---|---|
| Did the fixes work? | Measure · Before · After · Change |
| Day by day | Day · Off at · 7 PM · Lowest · Drain |
| Changes made | When · Change · How |
| What the model learned | Measure · Value |

### Setup
| Section | Controls |
|---|---|
| Your goal | Last till [− 11 PM +] · With [− 10% +] left |
| Automatic help | Heads-up switch · Smart saver switch · Saver below Off/20/30/40 |
| Permissions | 4 steps, each `Allow` → `✓ Done` |
| USB setup | Steps · commands (monospace) · Copy · Send to laptop · Check again |
| Privacy | One line |

## 5. Chart spec

| Element | Style |
|---|---|
| Window | 6 AM today (or the first reading) → target hour |
| Actual | Solid 3 dp, brand colour; green while charging |
| Forecast | Dashed 2.5 dp, status colour; dot at 0% |
| Buffer | Amber dotted line |
| Now | Thin dashed vertical |
| Axes | 0 / 50 / 100%; ticks every 3 h (`9a`, `12p`, `3p`…) |

## 6. Icon

Green battery outline with a white upward arrow, on a light-green adaptive background.

## 7. Copy rules

| Do | Don't |
|---|---|
| "0% around 7:40 PM" | "Estimated depletion timestamp" |
| "+25m/day" | "Saves 3.2% battery capacity" |
| "Opens settings" | "Requires manual intervention" |
