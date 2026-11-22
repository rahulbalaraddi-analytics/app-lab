#!/bin/bash
# Wi-Fi Speed Check for Mac (Apple Silicon or Intel, macOS 12+)
# READ-ONLY: this script only measures. It changes no settings and quits no apps.
# Takes about 2 minutes. Run:  bash wifi-check.sh

echo "== Wi-Fi Speed Check (takes ~2 min, changes nothing) =="
echo

# ---- 1. Speed test ----
echo "[1/4] Running speed test (3 times)..."
dl_sum=0; resp_max=0
for i in 1 2 3; do
  out=$(networkQuality 2>&1)
  dl=$(echo "$out" | awk '/Downlink capacity/{print $3}')
  ul=$(echo "$out" | awk '/Uplink capacity/{print $3}')
  rp=$(echo "$out" | awk '/Responsiveness/{gsub(/[(]/,""); print $3}')
  idle=$(echo "$out" | awk '/Idle Latency/{print $3}')
  echo "  Run $i: download ${dl} Mbps | upload ${ul} Mbps | delay under load ${rp} ms | idle delay ${idle} ms"
  dl_sum=$(echo "$dl_sum + ${dl:-0}" | bc -l)
  resp_max=$(echo "if (${rp:-0} > $resp_max) ${rp:-0} else $resp_max" | bc -l)
  idle_last=$idle
done
avg_dl=$(echo "scale=1; $dl_sum / 3" | bc -l)
echo

# ---- 2. Ping router and internet ----
echo "[2/4] Checking connection to your router and the internet..."
GW=$(route -n get default 2>/dev/null | awk '/gateway/{print $2}')
r=$(ping -c 20 -i 0.2 "$GW" 2>&1 | tail -2)
g=$(ping -c 20 -i 0.2 8.8.8.8 2>&1 | tail -2)
r_loss=$(echo "$r" | awk -F', ' '/packet loss/{print $3}' | awk '{print $1}')
g_loss=$(echo "$g" | awk -F', ' '/packet loss/{print $3}' | awk '{print $1}')
r_avg=$(echo "$r" | awk -F'/' '/round-trip/{print $5}')
g_avg=$(echo "$g" | awk -F'/' '/round-trip/{print $5}')
echo "  Router:  average ${r_avg} ms, lost packets ${r_loss}"
echo "  Internet: average ${g_avg} ms, lost packets ${g_loss}"
echo

# ---- 3. Wi-Fi details ----
echo "[3/4] Reading your Wi-Fi details..."
sp=$(system_profiler SPAirPortDataType 2>/dev/null)
cur=$(echo "$sp" | awk '/Current Network Information:/{f=1;next} /Other Local Wi-Fi Networks:/{f=0} f')
chan_line=$(echo "$cur" | awk '/Channel:/{sub(/^ *Channel: /,""); print; exit}')
chan=$(echo "$chan_line" | awk '{print $1}')
sig=$(echo "$cur" | awk -F': ' '/Signal \/ Noise/{print $2; exit}')
rate=$(echo "$cur" | awk -F': ' '/Transmit Rate/{print $2; exit}')
phy=$(echo "$cur" | awk -F': ' '/PHY Mode/{print $2; exit}')
rssi=$(echo "$sig" | awk '{print $1}')
echo "  Standard: $phy"
echo "  Channel:  $chan_line"
echo "  Signal / noise: $sig   (closer to 0 is stronger; -30 great, -67 ok, -80 bad)"
echo "  Link speed (Wi-Fi to router): ${rate} Mbps"
others=$(echo "$sp" | awk '/Other Local Wi-Fi Networks:/{f=1;next} /awdl0:/{f=0} f' | grep -c "Channel: ${chan} ")
echo "  Neighbours on the same channel as you: $others"
echo

# ---- 4. Who is using the network ----
echo "[4/4] Checking which apps use the network right now (5 sec)..."
nettop -P -L 2 -s 5 -x -J bytes_in,bytes_out 2>/dev/null | awk -F, 'NR>1 && $1!=""' | \
  awk -F, '{n=$1; sub(/\.[0-9]+$/,"",n); b[n]=$2+$3} END{for(k in b) print b[k], k}' | sort -rn | head -5 | \
  awk '{printf "  %-30s %.1f MB (total since app started)\n", $2" "$3" "$4, $1/1048576}'
echo "  (Look for: iCloud, Google Drive, OneDrive, Dropbox, Time Machine, software updates, VPN, torrents, Zoom)"
echo

# ---- Verdict ----
echo "================ VERDICT ================"
echo "Average download: ${avg_dl} Mbps | Wi-Fi link speed: ${rate} Mbps | Delay under load (worst): ${resp_max} ms"
verdict=0
if [ -n "$rssi" ] && [ "$rssi" -lt -75 ] 2>/dev/null; then
  echo "-> WEAK WI-FI SIGNAL. Move closer to the router or remove walls between you. This is likely your problem."
  verdict=1
fi
if [ "${others:-0}" -ge 3 ]; then
  echo "-> CROWDED CHANNEL ($others neighbours on yours). Change your router's Wi-Fi channel (see README)."
  verdict=1
fi
if [ -n "$rate" ] && [ "$(echo "$rate > 0 && $avg_dl < $rate / 4" | bc -l)" = "1" ] && [ $verdict -eq 0 ]; then
  echo "-> Wi-Fi is NOT the bottleneck (link speed is far higher than your internet speed)."
  echo "   Compare ${avg_dl} Mbps with the speed you PAY for."
  echo "   Close to your plan  = your plan is just small. Consider a faster plan."
  echo "   Far below your plan = call your internet provider and show them these numbers."
fi
if [ "$(echo "$resp_max > 300" | bc -l)" = "1" ]; then
  echo "-> LAG WHEN BUSY (bufferbloat): the connection gets laggy when something is downloading."
  echo "   Fix: pause big downloads/backups during calls, or turn on 'Smart Queue/QoS' in your router (see README)."
fi
echo "Nothing was changed on your Mac or router."
