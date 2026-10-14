# BatteryPilot

Make the battery last till **11 PM**. The app watches how your phone drains, learns your pattern on the phone itself, forecasts when you'll hit 0%, and gives you a **tick-box list of fixes**. It applies the ones you pick and then shows **before vs after**.

**Private:** no internet permission and no account. Everything is read and analysed on the phone.

## What it does (v0.1)

| Area | What you get |
|---|---|
| **Today** | Current %, forecast at 11 PM (or the time you'll hit 0%), drain now vs allowed %/hr, chart with forecast, screen-on time, temperature, battery health |
| **Where it's going** | Screen / mobile network / Wi-Fi / apps / idle split (exact after USB setup) |
| **Apps** | Battery per app since the last full charge, flags **background-heavy** apps |
| **Fixes** | Tick boxes with estimated **+minutes/day** each. One **Apply** button: Auto fixes are changed for you (with Undo). Guided fixes open the exact settings screen, one after another |
| **History** | Before/after table (avg, screen-on, screen-off %/hr), day-by-day table, change log, what the model learned |
| **Automatic** | One heads-up a day if you won't make 11 PM. **Smart saver** turns on Power saving only when the forecast misses. Optional "Power saving below X%" |

## The model (on-device)

| Part | How |
|---|---|
| Readings | Battery %, temperature, current, charging, screen state: every ~15 min + plug/unplug |
| Screen time | Android usage log (screen on/off events, foreground apps) |
| Hour-of-day profile | Learns your typical %/hr for each hour from up to 14 days |
| Screen-on vs off drain | Weighted linear regression: `rate = a + b × screen share` |
| Today factor | Today's real drain vs your usual, to adjust the rest of the day |
| Forecast | Walks forward in 5-min steps to your target hour, blending recent pace and the profile, with a range |

It starts with sensible defaults and gets sharper after a day or two of readings.

## Fixes

| Fix | Auto with | Otherwise |
|---|---|---|
| Adaptive brightness, screen timeout 30 s, touch vibration off | Modify system settings | Opens Display / Sound |
| 60 Hz, Power saving, Wi-Fi & Bluetooth scanning off, Smart saver | USB setup | Opens settings |
| Dark mode, Always On Display, Location, Bluetooth, 5G → 4G, put apps to sleep | — | Opens the exact screen |

## Install

1. On the phone, open **github.com/rahulbalaraddi-analytics/app-lab/releases/tag/batterypilot-latest**
2. Tap **BatteryPilot.apk** → open → allow "install unknown apps"
3. Updates install over the old version and keep the history (same signing key)

## Setup (in the app: ⚙)

1. **Usage access**: screen time + apps
2. **Modify system settings**: Auto fixes
3. **Notifications**: the 11 PM heads-up
4. **Battery optimisation off** + Samsung **Never sleeping apps**: readings keep coming
5. **One-time USB setup (optional, 5 min)** from a laptop with Android platform-tools:

```
adb shell pm grant com.applab.batterypilot android.permission.DUMP
adb shell pm grant com.applab.batterypilot android.permission.BATTERY_STATS
adb shell pm grant com.applab.batterypilot android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.applab.batterypilot GET_USAGE_STATS allow
```

This unlocks exact per-app battery, one-tap Power saving / 60 Hz / scanning, and Smart saver. It stays granted through updates.

## Tech

Kotlin · Jetpack Compose · Room · WorkManager · UsageStatsManager · `dumpsys batterystats` (with the granted permission)
