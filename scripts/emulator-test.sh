#!/usr/bin/env bash
# Test sur émulateur : autorisations, widget sur l'écran d'accueil, punch IN depuis le widget
set -x
mkdir -p out
PKG=ca.punch.heures
pip install -q websocket-client || true
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb emu geo fix -73.5673 45.5017 || true
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 20
adb exec-out screencap -p > out/1-autorisations.png
# Données de test : deux clients
python3 scripts/cdp_eval.py "localStorage.setItem('punch.v1', JSON.stringify({setupSeen:true, seenIntro:true, clients:[{id:'a',name:'Client A',rate:45,isDefault:true,lat:45.5017,lng:-73.5673},{id:'b',name:'Client B',rate:60,lat:45.55,lng:-73.5673}], entries:[]})); location.reload(); 'ok'"
sleep 8
# Ajouter le widget (demande d'épinglage Android)
adb shell am start -n $PKG/.MainActivity -a ca.punch.heures.WIDGET --es op pin
sleep 4
adb exec-out screencap -p > out/2-demande-widget.png
python3 scripts/ui_tap.py "^(add|ajouter)" || true
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 4
adb exec-out screencap -p > out/3-widget-hors-service.png
# Toucher IN sur le widget
python3 scripts/ui_tap.py "^IN$" || true
sleep 12
adb exec-out screencap -p > out/4-app-apres-in.png
adb shell input keyevent KEYCODE_HOME
sleep 4
adb exec-out screencap -p > out/5-widget-en-service.png
python3 scripts/cdp_eval.py "JSON.stringify({active: S.active && S.active.clientName, perms: permCache})" > out/etat.txt || true
adb logcat -d > out/logcat-complet.txt
grep -iE "chromium|Capacitor|console|AndroidRuntime|FATAL|PunchWidget|BackgroundGeo" out/logcat-complet.txt > out/logcat-app.txt || true
adb shell dumpsys appwidget | grep -iA3 punch > out/appwidget.txt || true
exit 0
