#!/usr/bin/env bash
# Personnalise la notification « En service » du suivi de position
set -e
F=android/app/src/main/res/values/strings.xml
grep -q capacitor_background_geolocation_notification_channel_name "$F" || sed -i 's#</resources>#    <string name="capacitor_background_geolocation_notification_channel_name">Punch en service</string>\n    <string name="capacitor_background_geolocation_notification_color">\#0f766e</string>\n</resources>#' "$F"
