# BatteryPilot — Flowcharts

> These render as diagrams on GitHub (Mermaid).

## 1. System overview

```mermaid
flowchart LR
    subgraph Phone["📱 Phone (nothing leaves it)"]
        BM[BatteryManager<br/>level, temp, current]
        US[Usage log<br/>screen on/off, apps]
        DS[dumpsys batterystats<br/>per-app mAh]
        W[SampleWorker<br/>every 15 min]
        R[Plug / unplug / boot<br/>receiver]
        DB[(Room DB<br/>samples, fix_log)]
        E[Engine]
        M[Drain model<br/>profile + regression]
        F[Forecast to 11 PM]
        FX[Fixes catalogue]
        UI[Today · Fixes · Apps · History]
        G[Guard<br/>alerts · smart saver]
        S[Android settings]
    end
    W --> BM
    R --> BM
    BM --> DB
    DB --> E
    US --> E
    DS --> E
    E --> M --> F
    E --> FX
    F --> UI
    FX --> UI
    UI -- Apply --> S
    UI -- log --> DB
    W --> G
    G --> F
    G -- Power saving --> S
```

## 2. Reading cycle (every ~15 min)

```mermaid
flowchart TD
    A[WorkManager fires] --> B[Read battery sticky intent]
    B --> C{Same as last reading<br/>within 60 s?}
    C -- yes --> D[Skip insert]
    C -- no --> E[Insert sample]
    E --> F[Delete samples older than 60 days]
    D --> G[Guard.check]
    F --> G
    G --> H{Charging?}
    H -- yes --> Z[Done]
    H -- no --> I{Level ≤ 'saver below X%'<br/>and USB setup?}
    I -- yes --> J[Power saving ON + notify] --> Z
    I -- no --> K{10 AM ≤ now < target?}
    K -- no --> Z
    K -- yes --> L[Train model + forecast]
    L --> M{Forecast ≥ buffer/2?}
    M -- yes --> Z
    M -- no --> N{Smart saver on, USB setup,<br/>won't make it, after 3 PM or ≤ 40%?}
    N -- yes --> O[Power saving ON + notify] --> Z
    N -- no --> P{Alerts on and<br/>not alerted today?}
    P -- yes --> Q[Heads-up notification] --> Z
    P -- no --> Z
```

## 3. Forecast

```mermaid
flowchart TD
    A[Samples, 14 days] --> B[On-battery intervals<br/>5 min–3 h gaps]
    U[Screen-on intervals] --> B
    B --> C[Hour-of-day profile<br/>+ prior]
    B --> D[Regression<br/>rate = a + b·screen]
    E[Since unplug: actual vs expected] --> F[Today factor 0.6–1.8]
    G[Last 90 min] --> H[Recent pace]
    C --> I
    F --> I
    H --> I[Step 5 min to target:<br/>blend recent → profile × factor]
    I --> J[% at target · 0% time · range]
    J --> K{≥ buffer?}
    K -- yes --> L[🟢 On track]
    K -- 0 to buffer --> M[🟠 Under buffer]
    K -- below 0 --> N[🔴 0% around hh:mm]
```

## 4. Apply fixes (user flow)

```mermaid
flowchart TD
    A[Fixes tab] --> B[Tick fixes<br/>or Select recommended]
    B --> C[Apply]
    C --> D{For each ticked fix}
    D -- Auto --> E[Save old value → write setting]
    E --> F{Allowed?}
    F -- yes --> G[Log 'applied']
    F -- SecurityException --> H[Open settings instead]
    D -- Guided --> I[Add to queue]
    G --> J{Queue empty?}
    H --> J
    I --> J
    J -- no --> K[Open first settings screen<br/>+ hint snackbar]
    K --> L[User flips switch, comes back]
    L --> M[Card: Next fix · Open settings / Stop]
    M --> K
    J -- yes --> N[Refresh → item moves to 'Already done ✓']
```

## 5. Before / after

```mermaid
flowchart LR
    A[Latest fix_log<br/>applied/opened] --> B[Change time T]
    B --> C[Intervals T−7d … T]
    B --> D[Intervals T … now]
    C --> E[Before: avg, screen-on, screen-off %/hr]
    D --> F[After: same]
    E --> G{Both ≥ 3 h?}
    F --> G
    G -- yes --> H[Change % → ✅ / ≈ / ⚠]
    G -- no --> I[Collecting data…]
```

## 6. First-run setup

```mermaid
flowchart TD
    A[Install APK] --> B[Open app] --> C[Setup opens itself]
    C --> D[Usage access] --> E[Modify system settings] --> F[Notifications] --> G[Battery optimisation off]
    G --> H{Laptop available?}
    H -- yes --> I[Developer options → USB debugging] --> J[Run 4 adb commands] --> K[Check again → ✓]
    H -- no --> L[Skip: estimates + Guided fixes]
    K --> M[Use phone for a day]
    L --> M
    M --> N[Fixes → Apply] --> O[History → before/after]
```
