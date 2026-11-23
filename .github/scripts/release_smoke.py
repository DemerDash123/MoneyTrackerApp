#!/usr/bin/env python3
"""Black-box smoke test of the installed *release* APK using only adb + uiautomator:
launch it, record an income through the real UI, visit every tab, and check for crashes."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.moneytracker.app"
OUT = sys.argv[1] if len(sys.argv) > 1 else "release"
os.makedirs(OUT, exist_ok=True)
results = []


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True)


def dump():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        r = adb("exec-out", "cat", "/sdcard/ui.xml")
        s = r.stdout.strip()
        if s.startswith("<?xml"):
            return ET.fromstring(s[s.index("<hierarchy"):] if "<hierarchy" in s else s)
        time.sleep(1.5)
    raise RuntimeError("uiautomator dump failed")


def find(root, text, contains=False):
    for n in root.iter("node"):
        t = n.get("text", "")
        d = n.get("content-desc", "")
        if t == text or d == text or (contains and (text in t or text in d)):
            return n
    return None


def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)


def shot(name):
    r = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True)
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(r.stdout)


def check(name, ok):
    results.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name, flush=True)


def alive():
    return adb("shell", "pidof", PKG).stdout.strip() != ""


def crashed():
    log = adb("logcat", "-d", "-b", "crash").stdout
    return "FATAL EXCEPTION" in log and PKG in log


adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
time.sleep(5)
root = dump()
check("release app launches and shows the dashboard", find(root, "Total balance", contains=True) is not None)
shot("01_launch")

n = find(root, "Income")
check("quick 'Income' action found", n is not None)
if n is not None:
    tap(n)
    root = dump()
    check("add transaction screen opens", find(root, "New transaction") is not None)
    adb("shell", "input", "text", "250")
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")  # hide keyboard
    time.sleep(1)
    root = dump()
    save = find(root, "Save")
    if save is None:
        adb("shell", "input", "swipe", "540", "1600", "540", "700", "300")
        time.sleep(1)
        root = dump()
        save = find(root, "Save")
    check("save button found", save is not None)
    if save is not None:
        tap(save)
        root = dump()
        check("income saved through release build (dashboard shows 250.000 OMR)", find(root, "250.000 OMR", contains=True) is not None)
        shot("02_after_income")

for label in ["Activity", "My Money", "Statistics", "Settings", "Home"]:
    root = dump()
    n = find(root, label)
    if n is None:
        check(f"tab '{label}' found", False)
        continue
    tap(n)
    shot("tab_" + label.replace(" ", "_").lower())
    check(f"tab '{label}' opens and app is alive", alive())

adb("shell", "am", "force-stop", PKG)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
time.sleep(5)
root = dump()
check("data still there after force-stop + relaunch", find(root, "250.000 OMR", contains=True) is not None)
check("no crash recorded in logcat", not crashed())

failed = [n for n, ok in results if not ok]
print(f"{len(results) - len(failed)} passed, {len(failed)} failed")
sys.exit(1 if failed else 0)
