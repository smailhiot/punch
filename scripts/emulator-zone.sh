#!/usr/bin/env bash
# Test émulateur : zone de 2 km surveillée par Android (géorepérage), sans GPS continu
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb shell dumpsys deviceidle whitelist +$PKG || true
adb shell cmd location set-location-enabled true || true
adb shell settings put secure location_mode 3 || true
adb shell settings put global network_location_opt_in 1 || true
fix() { adb emu geo fix $2 $1 || true; }
fix 45.5017 -73.5673
for k in 1 2 3; do fix 45.5017 -73.5673; sleep 2; done
adb shell am start -n $PKG/.MainActivity; sleep 20
timeout 60 python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, settings:{arrival:true}, clients:[{id:'a',name:'Client A',rate:45,isDefault:true,lat:45.5017,lng:-73.5673},{id:'b',name:'Client B',rate:60,lat:45.5500,lng:-73.5673}], entries:[]})); location.reload(); 'ok'"
sleep 10
timeout 60 python3 scripts/cdp_eval.py "punchIn('a').then(()=>JSON.stringify(S.active && S.active.startLoc))" > out/punch-in.txt
sleep 8
adb exec-out screencap -p > out/1-en-service.png
timeout 60 python3 scripts/cdp_eval.py "PN.getZoneState().then(r=>JSON.stringify(r))" > out/zone-1.txt || true
adb shell input keyevent KEYCODE_HOME; sleep 10
# GPS en arrière-plan ?
adb shell dumpsys location > out/location-arriere-plan.txt
adb shell dumpsys activity services $PKG > out/services-arriere-plan.txt
adb shell dumpsys notification --noredact | grep -iE "En service|quitté|android.title|android.text" > out/notifs-1.txt
# Ramener l'app au premier plan : l'émulateur ne fournit des positions au géorepérage que si une app en demande
adb shell am start -n $PKG/.MainActivity; sleep 8
# Quitter la zone : 1 km, puis 3 km, puis 5 km (fixes réguliers pendant ~4 min)
for k in $(seq 1 6); do fix 45.5107 -73.5673; sleep 10; done
for k in $(seq 1 30); do fix 45.5450 -73.5673; sleep 8; adb shell dumpsys notification --noredact | grep -q "quitté la zone" && break; done
adb shell dumpsys notification --noredact | grep -iE "android.title|android.text" > out/notifs-2.txt
adb shell cmd statusbar expand-notifications; sleep 3
adb exec-out screencap -p > out/2-notification.png
adb shell cmd statusbar collapse
timeout 60 python3 scripts/ui_tap.py "quitté la zone" || adb shell am start -n $PKG/.MainActivity
sleep 12
adb exec-out screencap -p > out/3-question.png
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({active:S.active&&S.active.clientName, left:S.active&&S.active.leftPending})" > out/etat.txt || true
timeout 60 python3 scripts/cdp_eval.py "PN.getZoneState().then(r=>JSON.stringify(r))" > out/zone-2.txt || true
adb shell dumpsys activity service com.google.android.gms/com.google.android.location.internal.GoogleLocationManagerService 2>/dev/null | grep -iA5 "geofence" | head -60 > out/gms-geofences.txt || true
adb logcat -d | grep -iE "Geofence|PunchZone|ca.punch|FATAL|AndroidRuntime" | grep -v "Handling local" | tail -80 > out/logcat-app.txt
exit 0
