#!/usr/bin/env python3
"""Ajoute le code natif de Punch au projet Android généré par Capacitor
(plugin PunchNative, widget, autorisations, textes)."""
import pathlib, re, shutil, sys

root = pathlib.Path(__file__).resolve().parent.parent
app = root / "android" / "app" / "src" / "main"
if not app.exists():
    sys.exit("Projet Android introuvable : lancez d'abord « npx cap add android »")

# 1. Code Java
java_dir = app / "java" / "ca" / "punch" / "heures"
java_dir.mkdir(parents=True, exist_ok=True)
for f in (root / "native" / "java").glob("*.java"):
    shutil.copy(f, java_dir / f.name)

# 2. Ressources (layout, xml, drawable)
for sub in ("layout", "xml", "drawable"):
    for f in (root / "native" / "res" / sub).glob("*"):
        dest = app / "res" / sub
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dest / f.name)

# 3. Manifeste : autorisation batterie + récepteur du widget
manifest = app / "AndroidManifest.xml"
m = manifest.read_text()
perm = '<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />'
if perm not in m:
    m = m.replace("</manifest>", f"    {perm}\n</manifest>")
receiver = """
        <receiver android:name=".PunchWidget" android:exported="false" android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/punch_widget_info" />
        </receiver>
"""
if "PunchWidget" not in m:
    m = re.sub(r"(\s*)</application>", receiver + r"\1</application>", m, count=1)
manifest.write_text(m)

# 4. Textes
strings = app / "res" / "values" / "strings.xml"
s = strings.read_text()
extra = {
    "capacitor_background_geolocation_notification_channel_name": "Punch en service",
    "capacitor_background_geolocation_notification_color": "#0f766e",
    "widget_description": "Punch IN / OUT en un toucher",
}
for k, v in extra.items():
    if f'name="{k}"' not in s:
        s = s.replace("</resources>", f'    <string name="{k}">{v}</string>\n</resources>')
strings.write_text(s)
# 5. Signature fixe : toujours la même clé, pour que les mises à jour s'installent par-dessus
import os
gradle = root / "android" / "app" / "build.gradle"
g = gradle.read_text()
if "signingConfigs" not in g:
    ks = (root / "keystore" / "debug.keystore").as_posix()
    g = g.replace("    buildTypes {", f"""    signingConfigs {{
        punch {{
            storeFile file("{ks}")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }}
    }}
    buildTypes {{
        debug {{
            signingConfig signingConfigs.punch
        }}""", 1)
# Numéro de version qui augmente à chaque construction
run = os.environ.get("GITHUB_RUN_NUMBER")
if run:
    g = re.sub(r"versionCode \d+", f"versionCode {int(run) + 100}", g)
    g = re.sub(r'versionName "[^"]*"', f'versionName "1.{run}"', g)
gradle.write_text(g)
print("Projet Android complété (plugin natif + widget + signature fixe).")
