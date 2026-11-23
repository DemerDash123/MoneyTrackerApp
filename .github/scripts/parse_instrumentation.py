#!/usr/bin/env python3
"""Parses `am instrument -r` raw output. Prints one line per test and a final summary line.
Exit code 0 only if at least one test ran and none failed."""
import sys

CODES = {"0": "PASS", "-1": "ERROR", "-2": "FAIL", "-3": "IGNORED", "-4": "ASSUMPTION"}


def main(path: str) -> int:
    text = open(path, encoding="utf-8", errors="replace").read()
    results = []
    current = {}
    key = None
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            body = line[len("INSTRUMENTATION_STATUS: "):]
            if "=" in body:
                key, value = body.split("=", 1)
                current[key] = value
            continue
        if line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = line.split(": ", 1)[1].strip()
            if code != "1":
                results.append((current.get("class", "?"), current.get("test", "?"), CODES.get(code, code), current.get("stack", "")))
            current = {}
            key = None
            continue
        if line.startswith("INSTRUMENTATION_"):
            key = None
            continue
        if key == "stack":
            current["stack"] = current.get("stack", "") + "\n" + line

    passed = sum(1 for r in results if r[2] in ("PASS", "IGNORED"))
    failed = [r for r in results if r[2] not in ("PASS", "IGNORED")]
    for cls, test, res, stack in results:
        print(f"{res:6} {cls.split('.')[-1]}#{test}")
        if res not in ("PASS", "IGNORED"):
            print("\n".join("       " + l for l in stack.strip().splitlines()[:25]))
    crashed = "Process crashed" in text or "INSTRUMENTATION_FAILED" in text
    if crashed:
        print("Instrumentation crashed:")
        print("\n".join(l for l in text.splitlines() if "crash" in l.lower() or "Exception" in l)[:2000])
    print(f"{passed} passed, {len(failed)} failed{' (process crashed)' if crashed else ''}")
    return 0 if passed > 0 and not failed and not crashed else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
