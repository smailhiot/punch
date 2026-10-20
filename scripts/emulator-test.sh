#!/usr/bin/env bash
# Test sur émulateur : sauvegarde automatique conservée après désinstallation + restauration
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
grant() { for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done; }
adb install -r Punch.apk && grant
adb emu geo fix -73.5673 45.5017 || true
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 18
# Données de test
python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, clients:[{id:'a',name:'Client A',rate:45,isDefault:true},{id:'b',name:'Client B',rate:60}], entries:[{id:'e1',clientId:'a',clientName:'Client A',rate:45,start:Date.now()-5*3600e3,end:Date.now()-3600e3,desc:'test',material:0}]})); location.reload(); 'ok'"
sleep 10
python3 scripts/cdp_eval.py "save(); 'ok'"; sleep 5
echo "--- avant désinstallation" > out/sauvegarde.txt
adb shell ls -la /sdcard/Documents/Punch/ >> out/sauvegarde.txt 2>&1
adb exec-out screencap -p > out/1-avec-donnees.png
adb uninstall $PKG
echo "--- après désinstallation" >> out/sauvegarde.txt
adb shell ls -la /sdcard/Documents/Punch/ >> out/sauvegarde.txt 2>&1
adb install Punch.apk && grant
adb shell am start -n $PKG/.MainActivity
sleep 20
adb exec-out screencap -p > out/2-reinstallee.png
python3 scripts/ui_tap.py "Restaurer une sauvegarde" || true
sleep 5
adb exec-out screencap -p > out/3-selecteur.png
python3 scripts/ui_tap.py "punch-sauvegarde" || { python3 scripts/ui_tap.py "^Documents$"; sleep 3; python3 scripts/ui_tap.py "^Punch$"; sleep 3; python3 scripts/ui_tap.py "punch-sauvegarde"; } || true
sleep 6
adb exec-out screencap -p > out/4-apres-restauration.png
python3 scripts/cdp_eval.py "JSON.stringify({clients:S.clients.length, entries:S.entries.length, err:autoBackupError})" > out/etat.txt || true
adb logcat -d > out/logcat-complet.txt
grep -iE "Capacitor|console|AndroidRuntime|FATAL|Filesystem" out/logcat-complet.txt > out/logcat-app.txt || true
exit 0
