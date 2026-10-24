#!/usr/bin/env bash
# Test émulateur : le bouton retour d'Android ferme la fiche sans enregistrer, et quitte la sélection
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb shell am start -n $PKG/.MainActivity; sleep 20
timeout 60 python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, clients:[{id:'a',name:'Client A',rate:45,isDefault:true}], entries:[{id:'e1',clientId:'a',clientName:'Client A',rate:45,start:new Date(2026,9,20,8,0).getTime(),end:new Date(2026,9,20,12,0).getTime(),material:0},{id:'e2',clientId:'a',clientName:'Client A',rate:45,start:new Date(2026,9,21,8,0).getTime(),end:new Date(2026,9,21,10,0).getTime(),material:0}]})); location.reload(); 'ok'"
sleep 8
timeout 60 python3 scripts/cdp_eval.py "go('history'); histPeriod='all'; renderHistory(); openEntry('e1'); document.getElementById('eStartT').value='06:00'; 'ok'"
sleep 2
adb exec-out screencap -p > out/1-fiche.png
adb shell input keyevent KEYCODE_BACK; sleep 2
adb exec-out screencap -p > out/2-apres-retour.png
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sheet: sheetOpen(), start: new Date(S.entries[0].start).getHours()})" > out/retour-fiche.txt
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" > out/premier-plan.txt
# Appui long puis retour
timeout 60 python3 scripts/ui_tap.py "^Client A$" || true
adb shell input swipe 300 700 300 700 900; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sel: selMode})" > out/selection-avant.txt
adb exec-out screencap -p > out/3-selection.png
adb shell input keyevent KEYCODE_BACK; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({sel: selMode, tab: current})" > out/selection-apres.txt
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" >> out/premier-plan.txt
# Retour depuis Historique → onglet Punch ; retour depuis Punch → l'app se ferme
adb shell input keyevent KEYCODE_BACK; sleep 2
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({tab: current})" > out/onglet-apres.txt
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity)" > out/fin.txt
exit 0
