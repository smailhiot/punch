#!/usr/bin/env bash
# Test émulateur : le clavier numérique s'ouvre en touchant l'heure d'une entrée
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb shell am start -n $PKG/.MainActivity; sleep 20
timeout 60 python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, clients:[{id:'a',name:'Client A',rate:45,isDefault:true}], entries:[{id:'e1',clientId:'a',clientName:'Client A',rate:45,start:new Date(2026,9,14,8,0).getTime(),end:new Date(2026,9,14,16,0).getTime(),desc:'test',material:0}]})); location.reload(); 'ok'"
sleep 8
timeout 60 python3 scripts/cdp_eval.py "go('history'); histPeriod='all'; renderHistory(); openEntry('e1'); 'ok'"
sleep 3
adb exec-out screencap -p > out/1-fiche.png
timeout 60 python3 scripts/ui_tap.py "^16:00$" || true
sleep 3
adb exec-out screencap -p > out/2-clavier.png
adb shell dumpsys input_method | grep -E "mInputShown|mImeWindowVis|inputType" > out/clavier.txt
adb shell input text "1730"; sleep 2
adb exec-out screencap -p > out/3-tape.png
timeout 60 python3 scripts/cdp_eval.py "JSON.stringify({fin: document.getElementById('eEndT').value, duree: document.getElementById('eDur').textContent})" > out/etat.txt || true
exit 0
