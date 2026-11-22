# Is my Wi-Fi slow? Find out in 2 minutes (Mac)

A free, **read-only** checker. It only measures. It does **not** change any setting or close any app.

It answers one question: **is the slowness from your Wi-Fi, your apps, or your router/internet provider?**

## How to use it

1. Open the **Terminal** app on your Mac (press `Cmd + Space`, type "Terminal", press Enter).
2. Copy and paste this, then press Enter:

```bash
curl -fsSL https://raw.githubusercontent.com/rahulbalaraddi-analytics/app-lab/main/wifi-speed-diagnosis/wifi-check.sh | bash
```

3. Wait about 2 minutes. Read the **VERDICT** at the bottom.

Needs macOS 12 or newer. Works on Apple Silicon and Intel Macs.
Tip: stay in the same room as your router, and pause big downloads while it runs.

## What the verdict means

| The script says | In plain words | What to do |
|---|---|---|
| **Weak Wi-Fi signal** | You are too far from the router. | Move closer, or add a Wi-Fi extender or mesh. |
| **Crowded channel** | Many neighbours' Wi-Fi is on the same channel as yours, like a busy road. | Change the channel on your router (below). |
| **Wi-Fi is not the bottleneck** | Your Wi-Fi is fast. The limit is your internet plan or provider. | Compare the speed with what you pay for (below). |
| **Lag when busy (bufferbloat)** | Fine when idle, laggy when something downloads. | Pause big downloads, or turn on Smart Queue (below). |

## Words explained simply

- **Mbps**: how fast data moves. Higher is faster.
- **Link speed**: how fast your Mac and router talk to each other over Wi-Fi. If this is much bigger than your internet speed, Wi-Fi is not your problem.
- **Signal (dBm)**: how strong Wi-Fi is. Closer to 0 is better. -30 is great, -67 is okay, -80 is bad.
- **Channel**: the lane your Wi-Fi uses. If neighbours use the same lane, everyone slows down.
- **Bufferbloat**: your router keeps a waiting line for data. When you download something big, the line gets long and your calls and clicks wait behind it. Your speed looks fine but everything feels laggy.
- **QoS / SQM ("Smart Queue")**: a router setting that keeps that waiting line short, so calls and browsing stay smooth during downloads. It does not make the internet faster.

## Fixes, from most to least useful

1. **Check your plan.** Look at your internet bill. If you pay for 30 Mbps and the test shows about 27, you are getting what you pay for. Only a faster plan raises that limit.
2. **If the speed is far below your plan:** test again with a cable (Ethernet) from your Mac to the router. Still slow with the cable? Call your provider and share the numbers.
3. **Restart the router.** Unplug it for 30 seconds, then plug it back in.
4. **Turn on Smart Queue / QoS** (only if your router has it). Look in the router settings for "QoS", "SQM", "Smart Queue", or "Adaptive QoS". Set the speed limits to about 90% of your plan. Many provider routers don't have this.
5. **Change the Wi-Fi channel** (only if the channel is crowded). Open your router page (the address is on the router sticker, often `192.168.0.1` or `192.168.1.1`). Go to Wi-Fi, then 5 GHz, then Channel. Pick 36, 40, 44 or 48. Save.
6. **Pause cloud sync and backups** (iCloud, Google Drive, OneDrive, Dropbox, Time Machine), software updates, VPN and torrents while on calls.

## Privacy

Everything runs on your own Mac. Nothing is sent to anyone, except the speed test, which uses Apple's own server. Read [wifi-check.sh](wifi-check.sh) first if you want to see what it does. It's short.

## Example result (from the author's Mac)

| What | Number | Meaning |
|---|---|---|
| Internet plan | 30 Mbps | What was paid for |
| Download | about 27 Mbps | About 90% of the plan, which is normal |
| Wi-Fi link speed | 866 Mbps | Wi-Fi is not the problem |
| Signal | -37 dBm | Excellent |
| Delay when busy | 435 to 616 ms | Bufferbloat: laggy when the connection is full |

Verdict: Wi-Fi was fine. The plan was small, and the router needed Smart Queue to feel smoother.
