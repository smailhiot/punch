#!/usr/bin/env python3
"""Touche le premier élément visible dont le texte correspond (test émulateur)."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1], re.I)
for attempt in range(5):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    try:
        root = ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
    except Exception:
        time.sleep(2); continue
    for n in root.iter("node"):
        label = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
        if pat.search(n.get("text") or "") or pat.search(n.get("content-desc") or ""):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
            print("tap:", label, n.get("bounds"))
            sys.exit(0)
    time.sleep(2)
print("introuvable:", sys.argv[1]); open("out/ui-last.xml", "w").write(xml)
