#!/usr/bin/env python3
"""Builds a Markdown summary of a CI run from JUnit XML and device test outputs."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

out = sys.argv[1]
gradle_rc = sys.argv[2] if len(sys.argv) > 2 else "?"
print("# CI summary\n")
print(f"Gradle build + unit tests exit code: `{gradle_rc}`\n")

print("## JVM unit tests\n")
total = fails = 0
rows = []
for f in sorted(glob.glob(os.path.join(out, "unit", "*.xml"))):
    root = ET.parse(f).getroot()
    t = int(root.get("tests", 0))
    fl = int(root.get("failures", 0)) + int(root.get("errors", 0))
    total += t
    fails += fl
    rows.append((root.get("name", "?").split(".")[-1], t, fl))
    for case in root.iter("testcase"):
        for bad in list(case.findall("failure")) + list(case.findall("error")):
            print(f"- FAIL `{case.get('classname','').split('.')[-1]}.{case.get('name')}`: {(bad.get('message') or '')[:300]}")
if rows:
    print("\n| Suite | Tests | Failed |\n|---|---:|---:|")
    for name, t, fl in rows:
        print(f"| {name} | {t} | {fl} |")
    print(f"\n**{total - fails}/{total} passed**\n")
else:
    print("No unit test results (build failed before tests?).\n")

print("## Device tests (Android emulator)\n")
summary = os.path.join(out, "device", "summary.tsv")
if os.path.exists(summary):
    print("| Suite | Result | Detail |\n|---|---|---|")
    for line in open(summary):
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 3:
            print(f"| {parts[0]} | {parts[1]} | {parts[2]} |")
    print()
    for f in sorted(glob.glob(os.path.join(out, "device", "*.txt"))):
        name = os.path.basename(f)
        if name.startswith("logcat"):
            continue
        print(f"<details><summary>{name}</summary>\n\n```\n{open(f, errors='replace').read()[-6000:]}\n```\n</details>\n")
else:
    print("Device tests did not run.\n")

shots = sorted(glob.glob(os.path.join(out, "device", "screens", "**", "*.png"), recursive=True))
print(f"## Screenshots ({len(shots)})\n")
for s in shots:
    print(f"- {os.path.relpath(s, out)}")

if gradle_rc not in ("0", "?"):
    print("\n## Build log (errors)\n\n```")
    try:
        lines = open(os.path.join(out, "build.log"), errors="replace").read().splitlines()
        errs = [l for l in lines if l.startswith("e: ") or "FAILED" in l or "error:" in l or "What went wrong" in l]
        print("\n".join(errs[:200]))
    except OSError:
        pass
    print("```")
