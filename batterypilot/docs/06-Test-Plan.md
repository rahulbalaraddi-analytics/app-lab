# BatteryPilot — Test Plan

## 1. Automated (runs in CI on every push)

| # | Test | Checks | Status |
|---|---|---|---|
| U1 | `checkinParserReadsAppsAndParts` | `pwi` rows, only "since charged" (`l`), uid kept | ✅ Pass |
| U2 | `humanParserReadsUidLines` | `UID u0a234` → 10234, `Screen:` part | ✅ Pass |
| U3 | `lastUnplugFindsMorning` | Unplug time; null while charging | ✅ Pass |
| U4 | `modelLearnsRateAndForecastMissesElevenPm` | Learns 8.3%/hr (±1); 0% between 6–9:30 PM | ✅ Pass |
| U5 | `forecastMakesItOnALightDay` | 4%/hr day → makes 11 PM | ✅ Pass |
| U6 | `beforeAfterShowsImprovement` | 8.3 → 5.5%/hr shows 20–45% better | ✅ Pass |

## 2. Manual — on the Samsung phone

### A. Install & setup

| # | Step | Expected | ✓ |
|---|---|---|---|
| A1 | Install APK from release page | Installs, icon visible | ☐ |
| A2 | Open first time | Setup screen opens by itself | ☐ |
| A3 | Usage access → Allow | Step shows ✓ Done on return | ☐ |
| A4 | Modify system settings → Allow | ✓ Done | ☐ |
| A5 | Notifications → Allow | ✓ Done | ☐ |
| A6 | Battery optimisation → Allow | ✓ Done | ☐ |
| A7 | adb commands → Check again | USB setup "Done ✓" | ☐ |
| A8 | Install a newer build over it | History kept, permissions kept | ☐ |

### B. Data collection

| # | Step | Expected | ✓ |
|---|---|---|---|
| B1 | Leave phone 1 h, open app | Chart shows ~4 new points | ☐ |
| B2 | Plug in, unplug | Green segment on chart; "Off charger" time updates | ☐ |
| B3 | Overnight | Readings continue (maybe sparser); no gap > 3 h while awake | ☐ |
| B4 | Reboot phone | Readings resume without opening app | ☐ |
| B5 | Compare with Settings → Battery | Apps tab top 5 ≈ same order and % | ☐ |

### C. Forecast

| # | Step | Expected | ✓ |
|---|---|---|---|
| C1 | Day 1, 2 PM | Forecast shown, confidence "Learning" | ☐ |
| C2 | Note 5 PM forecast for 11 PM | Write down: ____% | ☐ |
| C3 | Actual at 11 PM (or 0% time) | Within ±8% / ±45 min of C2 by day 3 | ☐ |
| C4 | Heavy use 30 min (video) | "Draining now" rises; forecast drops | ☐ |
| C5 | After 11 PM | "Day done — charge overnight" | ☐ |

### D. Fixes

| # | Fix | Expected after Apply | Undo works | ✓ |
|---|---|---|---|---|
| D1 | Adaptive brightness | Display settings shows it ON | Yes | ☐ |
| D2 | Timeout 30 s | Display → Screen timeout = 30 s | Yes | ☐ |
| D3 | 60 Hz (USB setup) | Motion smoothness = Standard | Yes | ☐ |
| D4 | Power saving (USB setup) | Quick panel tile ON | Yes | ☐ |
| D5 | Scanning off (USB setup) | Location services → both scanning OFF | Yes | ☐ |
| D6 | Dark mode (guided) | Display settings opens | — | ☐ |
| D7 | AOD (guided) | Lock screen / AOD settings opens | — | ☐ |
| D8 | 5G → 4G (guided) | Mobile networks opens | — | ☐ |
| D9 | Put app to sleep | App info page opens | — | ☐ |
| D10 | Tick 3 auto + 2 guided → Apply | 3 done at once; "Next:" card walks through 2 | — | ☐ |
| D11 | Without permissions | Auto fixes fall back to opening settings | — | ☐ |

### E. Alerts & Smart saver

| # | Setup | Expected | ✓ |
|---|---|---|---|
| E1 | Alerts on, heavy day | One notification after 10 AM; not repeated same day | ☐ |
| E2 | Tap "Turn on Power saving" | Power saving ON; logged in History | ☐ |
| E3 | Smart saver on, forecast miss after 3 PM | Power saving turns on by itself + notification | ☐ |
| E4 | Saver below 30% | Turns on at ≤ 30% | ☐ |

### F. Before / after

| # | Step | Expected | ✓ |
|---|---|---|---|
| F1 | Apply fixes on day 2 | History shows "Collecting ‘after’ data" | ☐ |
| F2 | Day 3 evening | Before/After table filled; change % shown | ☐ |
| F3 | Goal check | Battery ≥ 10% at 11 PM on a normal weekday | ☐ |

## 3. Edge cases

| Case | Expected |
|---|---|
| No readings yet | "Taking the first reading…", no crash |
| Phone off for hours | Gap skipped (> 3 h), no fake drain spike |
| Charged mid-day briefly | New "off charger" point; forecast uses it |
| `dumpsys` denied / new format | Apps tab falls back to screen-time estimate |
| Target hour changed to 10 PM | Forecast + alerts follow new target |
| Battery health < 80% | Red warning on Today |
| Temperature ≥ 40°C | Temperature tile red, "hot — drains faster" |

## 4. Bug report format

| Field | Example |
|---|---|
| Screen | Fixes |
| Step | Tapped Apply on 60 Hz |
| Expected | Motion smoothness = Standard |
| Actual | Still Adaptive |
| Phone / One UI | Galaxy ___ / One UI ___ |
| Screenshot | Attach |
