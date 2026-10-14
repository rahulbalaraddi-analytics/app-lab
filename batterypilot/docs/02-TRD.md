# BatteryPilot — TRD (Technical Requirements & Design)

## 1. Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 (dynamic colour) | BOM 2024.12.01 |
| Storage | Room (SQLite) + SharedPreferences | 2.6.1 |
| Background | WorkManager periodic (15 min) + broadcast receivers | 2.10.0 |
| Build | AGP 8.7.3, Gradle 8.11.1, JDK 17 | — |
| SDK | min 29 (Android 10), target/compile 35 | — |
| CI | GitHub Actions → unit tests → APK → `batterypilot-latest` release | — |

## 2. Modules (package `com.applab.batterypilot`)

| Package | File | Responsibility |
|---|---|---|
| `collect` | `BatteryReader` | Reads sticky `ACTION_BATTERY_CHANGED` + `BatteryManager` (level, temp, current, charge counter, health, cycles) |
| | `UsageReader` | `UsageStatsManager.queryEvents` → screen-on intervals, wake-ups, per-app foreground time |
| | `BatteryStatsReader` | Runs `dumpsys batterystats` (needs DUMP) → parses per-app + per-part mAh |
| | `Access` | Live permission state + adb commands |
| | `SampleWorker` | Worker + `PowerEventReceiver` + `AlertActionReceiver` + `Guard` (alerts / smart saver) |
| `data` | `Database` | Room: `samples`, `fix_log` |
| | `Prefs` | Target hour, buffer, toggles, undo values |
| `analysis` | `DrainModel` | `Analysis`: intervals, training, forecast, before/after, day summaries |
| | `Engine` | Pulls collect + analysis + fixes into one `Snapshot` for UI |
| `fixes` | `Fixes` | Fix catalogue: detect, estimate saving, apply, undo, open guided screen |
| `ui` | `*Screen.kt` | Today, Fixes, Apps, History, Setup; `BatteryChart` (Canvas) |

## 3. Data collection

| Trigger | Source tag | Frequency |
|---|---|---|
| WorkManager periodic | `worker` | ~15 min (Doze may stretch overnight) |
| Plug in / unplug | `plug` / `unplug` | Event |
| Boot / app update | `boot` / `update` | Event |
| App opened / resumed | `open` | Event |

- **Duplicate guard:** skip insert if < 60 s since last AND level and charging unchanged.
- **Retention:** 60 days (rolling delete on each insert).

## 4. Per-app battery (exact)

| Step | Detail |
|---|---|
| Permission | `android.permission.DUMP` granted via `adb shell pm grant` (protection level includes `development`) |
| Command 1 | `dumpsys batterystats --charged --checkin` → parse `…,l,pwi,<label>,<mAh>,…` rows |
| Command 2 (fallback) | `dumpsys batterystats --checkin` |
| Command 3 (fallback) | `dumpsys batterystats --charged` → parse "Estimated power use (mAh)" section |
| uid → app | `PackageManager.getPackagesForUid`; app id < 10000 → "Android system" |
| Labels | `scrn`→Screen, `cell`→Mobile network, `wifi`, `blue`, `idle`, `gnss`, `ambi`→AOD … |
| Timeout | 25 s; output is streamed and only `pwi/pws` lines are kept (history can be MBs) |
| No permission | Falls back to screen-time-based estimate |

## 5. The model (on-device ML)

| Component | Method | Defaults (prior) |
|---|---|---|
| Intervals | Consecutive on-battery samples, 5 min ≤ gap ≤ 3 h, level not rising | — |
| **Hour-of-day profile** | Duration-weighted mean %/hr per clock hour, 14 days, + 1 h prior per slot | 6%/hr (7 AM–10 PM), 1.5%/hr night |
| **Screen regression** | Weighted least squares: `rate = a + b·screenShare`, + 2 pseudo-points | a = 1.2, a+b = 13 %/hr |
| Clamp | a ∈ [0.1, 10], b ∈ [1, 60] | — |
| **Today factor** | Actual drop since unplug ÷ profile-expected drop; shrunk to 1.0 for < 6 h data; clamp 0.6–1.8 | 1.0 |
| Recent pace | %/hr over last 90 min (needs ≥ 30 min) | — |
| **Forecast** | 5-min steps to target: `rate = w·recent + (1−w)·profile[h]·todayFactor`, `w = 0.6·e^(−t/1.5h)` | — |
| Range | ± model `rateStd` (half of the hourly-rate SD, clamp 0.5–6) | 2 |
| Confidence | Learning < 12 h data ≤ Medium < 40 h ≤ High | — |

## 6. Fix savings estimate

`screenDrain/day = screenOnRate × screenHours/day`; minutes = `saved% ÷ avgRate × 60`

| Fix | Saving formula (% per day) |
|---|---|
| Adaptive brightness | screenDrain × 0.45 × 0.18 |
| Timeout 30 s | screenDrain × min(0.1, 0.025 × log2(timeout/30 s)) |
| 60 Hz | screenDrain × 0.10 |
| Dark mode | screenDrain × 0.45 × 0.20 |
| AOD off | 0.6 × screen-off hours |
| Smart saver | avgRate × 2 h × 0.25 |
| Power saving | avgRate × 6 h × 0.20 |
| Scanning off | 0.08 × screen-off hours |
| 5G → 4G | 30% of mobile-network share (or 1.5) |
| App to sleep | 70% of that app's share |

## 7. Applying fixes

| Fix | Setting written | Permission |
|---|---|---|
| Adaptive brightness | `System.SCREEN_BRIGHTNESS_MODE = 1` | WRITE_SETTINGS |
| Timeout | `System.SCREEN_OFF_TIMEOUT = 30000` | WRITE_SETTINGS |
| Vibration | `System.HAPTIC_FEEDBACK_ENABLED = 0` | WRITE_SETTINGS |
| 60 Hz | `Secure.refresh_rate_mode = 0` (Samsung) + `peak/min_refresh_rate = 60` | WRITE_SECURE_SETTINGS |
| Power saving | `Global.low_power = 1` | WRITE_SECURE_SETTINGS |
| Scanning | `Global.wifi_scan_always_enabled = 0`, `ble_scan_always_enabled = 0` | WRITE_SECURE_SETTINGS |
| Guided | Opens intent chain (Samsung component → AOSP action → Settings) | — |

- **Undo:** old value saved in Prefs (`prev_<fixId>`) before writing.
- **Logging:** every apply/open/undo → `fix_log` (drives before/after).
- `SecurityException` → automatic fallback to Guided.

## 8. Background rules (`Guard`, every worker run)

| Order | Rule | Condition | Action |
|---|---|---|---|
| 1 | Saver below X% | level ≤ X, not charging, USB setup, once/day | Power saving on + notify |
| 2 | Smart saver | forecast misses target, after 3 PM or ≤ 40%, once/day | Power saving on + notify |
| 3 | Heads-up | 10 AM–target, forecast < buffer/2, once/day | Notify (+ button if USB setup) |

## 9. Non-functional

| Item | Requirement |
|---|---|
| Privacy | No `INTERNET` permission; `allowBackup=false` |
| Own drain | Worker < 1 s CPU per run; `dumpsys` only when UI opens |
| Signing | Shared debug key in repo → updates install over each other |
| Tests | JVM unit tests: parsers, unplug detection, model accuracy, forecast, before/after |
