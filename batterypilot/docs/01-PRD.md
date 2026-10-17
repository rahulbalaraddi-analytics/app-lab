# BatteryPilot — PRD (Product Requirements)

| Field | Value |
|---|---|
| App | BatteryPilot (Android) |
| Version | v0.2 |
| Owner | rahulbalaraddi-analytics |
| Status | Built, awaiting first test on a real phone |
| Updated | 15 Oct 2026 |

## 1. Problem

| Today | Goal |
|---|---|
| 100% at ~7:30 AM | Same |
| 20–30% by 4–5 PM | ≥ 40% at 5 PM |
| **0% by ~7 PM** | **≥ 10% at 11 PM** |
| Drain ≈ **8.3%/hr** | Drain ≤ **5.6%/hr** (≈ 30–35% less) |

- No easy way to see **where** the battery goes, or whether a change actually helped.
- Doesn't want to copy screenshots to anyone. **Everything must happen on the phone.**

## 2. Users

| User | Need |
|---|---|
| **Primary:** Samsung phone owner, heavy day user, charges overnight | Last till 11 PM without thinking about it |
| Later: any Android user (Play Store) | Same, on any brand |

## 3. Goals / non-goals

| ✅ Goals | ❌ Non-goals (v0.1) |
|---|---|
| Measure drain on the phone itself | Cloud sync / accounts |
| Forecast "will I make 11 PM?" | Root-only tricks |
| Tick-box fixes, applied with one tap | Killing other apps |
| Show before vs after | Battery-replacement advice beyond a warning |
| 100% private, no internet | Play Store release (later) |

## 4. Features (MVP)

| # | Feature | Must-have details | Priority |
|---|---|---|---|
| F1 | **Drain dashboard** | Current %, %/hr now, screen-on time, temperature, health, chart of today | P0 |
| F2 | **11 PM predictor** | Expected % at target, time of 0%, allowed %/hr, range, model confidence | P0 |
| F3 | **Where it's going** | Screen / network / Wi-Fi / apps / idle split; per-app list; flag background-heavy apps | P0 |
| F3b | **Foreground vs background** (v0.2) | Per app: on-screen vs background mAh (Android 13+), time running hidden, foreground service, kept-awake (wakelocks), jobs, mobile/Wi-Fi data; reasons + "what to do"; filter "Background activity" | P0 |
| F4 | **Tick-box fixes** | Each fix shows +minutes/day, Auto or Guided; **Apply** button; **Undo** | P0 |
| F5 | **Before / after** | Avg, screen-on, screen-off %/hr before vs after a change | P0 |
| F6 | **Heads-up alert** | Max 1/day when forecast misses target; button to turn on Power saving | P1 |
| F7 | **Smart saver** | Auto Power saving only when the forecast misses (after 3 PM or < 40%) | P1 |
| F8 | **Saver below X%** | 20 / 30 / 40% rule | P2 |
| F9 | **Settings** | Target hour (default 11 PM), buffer (default 10%) | P0 |

## 5. Fixes list (v0.1)

| Fix | Auto? | Detected? |
|---|---|---|
| Adaptive brightness | ✅ (Modify system settings) | ✅ |
| Screen timeout → 30 s | ✅ | ✅ |
| Refresh rate → 60 Hz | ✅ (USB setup) | ✅ |
| Dark mode | Guided | ✅ |
| Always On Display off | Guided | Samsung only |
| Smart saver | ✅ (USB setup) | ✅ |
| Power saving now | ✅ (USB setup) | ✅ |
| Wi-Fi & Bluetooth scanning off | ✅ (USB setup) | ✅ |
| Location off | Guided | ✅ |
| Bluetooth off | Guided | ✅ |
| 5G → 4G | Guided | ❌ |
| Touch vibration off | ✅ | ✅ |
| Put background-heavy app to sleep | Guided | ✅ (needs USB setup) |

## 6. Success metrics

| Metric | Target |
|---|---|
| Battery at 11 PM (weekday avg, week 2) | **≥ 10%** |
| Avg drain %/hr after fixes vs before | **−30%** |
| Forecast error at 5 PM for 11 PM level | **≤ ±8%** after 3 days |
| Background readings per day | ≥ 60 (≈ every 15 min, awake hours) |
| App's own drain | < 0.5% per day |

## 7. Permissions (what the user grants)

| Permission | Why | How |
|---|---|---|
| Usage access | Screen time, per-app use | Settings toggle |
| Modify system settings | Brightness / timeout fixes | Settings toggle |
| Notifications | Heads-up | Popup |
| Ignore battery optimisation | Readings keep coming | Popup |
| DUMP / BATTERY_STATS / WRITE_SECURE_SETTINGS | Exact per-app, Power saving, 60 Hz | **One-time adb** |

## 8. Milestones

| M | Scope | Status |
|---|---|---|
| **M1** | F1–F9 (this PRD) | ✅ Built |
| M2 | Home-screen widget, weekly report, per-app before/after | Planned |
| M3 | Smarter model (weekday/weekend, location-aware: office vs home), Shizuku instead of laptop | Planned |
| M4 | Play Store release (release key, privacy policy, non-Samsung support) | Planned |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Samsung puts the app to sleep → gaps | Ask for "Never sleeping apps" + battery-optimisation exemption |
| Samsung settings keys change between One UI versions | Fallback to Guided; detection shows "unknown" |
| `dumpsys` output format changes | 3 parsers (checkin → checkin all → human-readable), estimate fallback |
| Battery itself is worn (< 80% health) | Warning on Today screen |
| Laptop needed for USB setup | Optional; app works without it |
