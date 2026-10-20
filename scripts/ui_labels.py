#!/usr/bin/env python3
"""Écrit les textes visibles à l'écran (test émulateur)."""
import re, subprocess, sys
subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
x = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
labels = [l for l in re.findall(r'(?:text|content-desc)="([^"]+)"', x)]
open(sys.argv[1], "w").write("\n".join(labels))
