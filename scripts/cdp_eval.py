#!/usr/bin/env python3
"""Exécute du JavaScript dans la WebView de l'app (APK de débogage, test émulateur)."""
import json, re, subprocess, sys, time, urllib.request
import websocket
js = sys.argv[1]
for attempt in range(10):
    socks = subprocess.run(["adb", "shell", "cat", "/proc/net/unix"], capture_output=True, text=True).stdout
    names = re.findall(r"@(webview_devtools_remote_\d+)", socks)
    if names: break
    time.sleep(2)
subprocess.run(["adb", "forward", "tcp:9222", "localabstract:" + names[-1]], check=True)
pages = json.load(urllib.request.urlopen("http://127.0.0.1:9222/json"))
page = [p for p in pages if p.get("type") == "page"][0]
ws = websocket.create_connection(page["webSocketDebuggerUrl"], suppress_origin=True)
ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate", "params": {"expression": js, "returnByValue": True, "awaitPromise": True}}))
print(json.loads(ws.recv()).get("result", {}).get("result", {}).get("value"))
