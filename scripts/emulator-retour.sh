#!/usr/bin/env bash
# Test émulateur : le bouton retour d'Android ferme la fiche sans enregistrer, et quitte la sélection
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb logcat -c
adb shell am start -n $PKG/.MainActivity; sleep 20
ensure_app() { for k in 1 2 3; do adb shell dumpsys activity activities | grep -qE "(mResumedActivity|topResumedActivity).*ca.punch.heures" && return 0; adb shell am start -n $PKG/.MainActivity; sleep 15; done; }
ensure_app
adb exec-out screencap -p > out/0-demarrage.png
timeout 60 python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, clients:[{id:'a',name:'Client A',rate:45,isDefault:true}], entries:[{id:'e1',clientId:'a',clientName:'Client A',rate:45,start:new Date(2026,9,20,8,0).getTime(),end:new Date(2026,9,20,12,0).getTime(),material:0},{id:'e2',clientId:'a',clientName:'Client A',rate:45,start:new Date(2026,9,21,8,0).getTime(),end:new Date(2026,9,21,10,0).getTime(),material:0}]})); location.reload(); 'ok'"
sleep 8
ensure_app
timeout 60 python3 scripts/cdp_eval.py "go('history'); histPeriod='all'; renderHistory(); openEntry('e1'); document.getElementById('eStartT').value='06:00'; 'ok'"
sleep 2
adb exec-out screencap -p > out/1-fiche.png
adb shell input keyevent KEYCODE_BACK; sleep 2
adb exec-out screencap -p > out/2-apres-retour.png
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sheet: sheetOpen(), start: new Date(S.entries[0].start).getHours()})" > out/retour-fiche.txt
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" > out/premier-plan.txt
# Appui long puis retour
LONG=1 timeout 60 python3 scripts/ui_tap.py "08:00.12:00" || true; sleep 2
timeout 60 python3 scripts/ui_tap.py "08:00.10:00" || true; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sel: selMode, ids: [...selIds], sheet: sheetOpen()})" > out/selection-avant.txt
adb exec-out screencap -p > out/3-selection.png
adb shell input keyevent KEYCODE_BACK; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sel: selMode, tab: current})" > out/selection-apres.txt
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" >> out/premier-plan.txt
# Retour depuis Historique → onglet Punch ; retour depuis Punch → l'app se ferme
adb shell input keyevent KEYCODE_BACK; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({tab: current})" > out/onglet-apres.txt
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" > out/fin.txt
adb logcat -d | grep -iE "AndroidRuntime|FATAL|ca.punch|Capacitor/Console|chromium" | grep -v "Handling local" | tail -150 > out/logcat-app.txt
exit 0
