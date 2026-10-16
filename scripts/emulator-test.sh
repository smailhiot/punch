#!/usr/bin/env bash
# Installe l'APK sur l'émulateur, lance l'app et récupère captures + journaux
set -x
mkdir -p out
PKG=ca.punch.heures
adb install -r Punch.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do adb shell pm grant $PKG android.permission.$p || true; done
adb emu geo fix -73.5673 45.5017 || true
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 25
adb exec-out screencap -p > out/1-clair.png
adb shell cmd uimode night yes
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity
sleep 20
adb exec-out screencap -p > out/2-sombre.png
adb logcat -d > out/logcat-complet.txt
grep -iE "chromium|Capacitor|console|AndroidRuntime|FATAL|WebView|BackgroundGeo|punch" out/logcat-complet.txt > out/logcat-app.txt || true
adb shell dumpsys package $PKG | grep -iE "permission|targetSdk|versionName" > out/package.txt || true
adb shell getprop ro.build.version.release > out/android-version.txt
exit 0
