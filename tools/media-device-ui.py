#!/usr/bin/env python3
"""ADB UI observations for the explicitly requested Android emulator validation."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["dump", "tap", "swipe", "shot", "back", "home", "text"])
parser.add_argument("value", nargs="?", default="")
parser.add_argument("--serial", default="emulator-5554")
args = parser.parse_args()
adb = ["/home/hanan-bhatti/Android/Sdk/platform-tools/adb", "-s", args.serial]
def command(*words):
    return subprocess.check_output(adb + list(words), timeout=30)
def observe():
    for attempt in range(3):
        result = command("shell", "uiautomator", "dump", "/sdcard/cobalt-window.xml")
        if b"dumped to" in result:
            data = command("exec-out", "cat", "/sdcard/cobalt-window.xml")
            Path("/tmp/cobalt-window.xml").write_bytes(data)
            return ET.fromstring(data)
        time.sleep(1)
    raise SystemExit("No fresh UI snapshot available")
if args.action == "shot":
    Path(args.value).write_bytes(command("exec-out", "screencap", "-p"))
elif args.action in ("back", "home"):
    command("shell", "input", "keyevent", "4" if args.action == "back" else "3")
elif args.action == "text":
    command("shell", "input", "text", args.value.replace(" ", "%s"))
elif args.action == "swipe":
    command("shell", "input", "swipe", *args.value.split())
elif args.action == "tap":
    root = observe()
    candidates = [n for n in root.iter("node") if args.value in (n.get("text", ""), n.get("content-desc", ""))]
    for node in candidates:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        if x2 > x1 and y2 > y1:
            command("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            break
    else:
        raise SystemExit("Visible target not found: " + args.value)
if args.action != "shot":
    for node in observe().iter("node"):
        label = node.get("text") or node.get("content-desc")
        if label:
            if node.get("password") == "true" or node.get("class") == "android.widget.EditText":
                label = "[editable field]"
            print(label[:170], node.get("bounds"))
