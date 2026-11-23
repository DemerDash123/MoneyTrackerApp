#!/usr/bin/env bash
# Runs every on-device check against the emulator. Never stops at the first failure:
# each suite is recorded in ci-out/device/summary.tsv and the script exits non-zero if any failed.
set -u
OUT=ci-out/device
mkdir -p "$OUT/screens"
PKG=com.moneytracker.app
TPKG=$PKG.test
RUNNER=$TPKG/androidx.test.runner.AndroidJUnitRunner
STATUS=0
SUMMARY=$OUT/summary.tsv
: > "$SUMMARY"

log() { echo "[$(date +%T)] $*"; }

record() { # name result detail
  printf "%s\t%s\t%s\n" "$1" "$2" "$3" >> "$SUMMARY"
  log "$2 $1 - $3"
  [ "$2" = PASS ] || STATUS=1
}

instr() { # name class [extra -e args...]
  local name=$1 cls=$2
  shift 2
  log "instrumenting $name ($cls)"
  adb shell am instrument -w -r -e class "$cls" "$@" "$RUNNER" > "$OUT/$name.raw" 2>&1
  if python3 .github/scripts/parse_instrumentation.py "$OUT/$name.raw" > "$OUT/$name.txt"; then
    record "$name" PASS "$(tail -n 1 "$OUT/$name.txt")"
  else
    record "$name" FAIL "$(tail -n 1 "$OUT/$name.txt")"
  fi
}

wait_boot() {
  adb wait-for-device
  for _ in $(seq 1 150); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then break; fi
    sleep 2
  done
  sleep 8
  adb shell input keyevent 82 || true
  adb shell settings put global window_animation_scale 0 || true
  adb shell settings put global transition_animation_scale 0 || true
  adb shell settings put global animator_duration_scale 0 || true
}

pull_screens() {
  adb exec-out run-as "$PKG" tar cf - files/screenshots 2>/dev/null | tar xf - -C "$OUT/screens" 2>/dev/null || true
}

demo_mode() {
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null
}

adb shell getprop ro.build.version.release | tee "$OUT/android-version.txt"
adb shell wm size | tee "$OUT/screen.txt"
adb logcat -c || true
demo_mode

adb install -r -t app/build/outputs/apk/debug/app-debug.apk > "$OUT/install.txt" 2>&1 || record install_debug FAIL "adb install failed"
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >> "$OUT/install.txt" 2>&1 || record install_test FAIL "adb install failed"

# 1. Repository / database integration tests
instr repository com.moneytracker.app.FinanceRepositoryTest
# 2. End-to-end UI flows
instr ui_flows com.moneytracker.app.UiFlowTest
# 2b. Arabic: first-launch picker, switching both ways, and money flows in the RTL interface
instr language_first_launch com.moneytracker.app.FirstLaunchLanguageTest
instr language_switch com.moneytracker.app.LanguageSwitchTest
instr arabic_flows com.moneytracker.app.ArabicFlowTest
# 3. Screenshot tour (every screen, light + dark) on a phone-size screen, in English and Arabic
instr screens_phone com.moneytracker.app.ScreenshotTour -e prefix phone -e full true -e lang en
instr screens_phone_ar com.moneytracker.app.ScreenshotTour -e prefix phone_ar -e full true -e lang ar
pull_screens

# 4. Different screen sizes
adb shell wm size 720x1280
adb shell wm density 320
instr screens_small com.moneytracker.app.ScreenshotTour -e prefix small -e full false -e lang en
instr screens_small_ar com.moneytracker.app.ScreenshotTour -e prefix small_ar -e full false -e lang ar
adb shell wm size 1600x2560
adb shell wm density 320
instr screens_tablet com.moneytracker.app.ScreenshotTour -e prefix tablet -e full false -e lang en
adb shell wm size reset
adb shell wm density reset
pull_screens

# 5. Persistence: write, kill the app, verify; reboot the emulator, verify again
instr persist_write com.moneytracker.app.PersistenceWriteTest
adb shell am force-stop "$PKG"
instr persist_after_force_stop com.moneytracker.app.PersistenceVerifyTest
log "rebooting emulator"
adb reboot
wait_boot
adb shell getprop sys.boot_completed > "$OUT/boot_after_reboot.txt" 2>&1
instr persist_after_reboot com.moneytracker.app.PersistenceVerifyTest

adb logcat -d > "$OUT/logcat-debug.txt" 2>&1 || true

# 6. The actual release APK (minified, signed): install on a clean device state and use it
adb uninstall "$TPKG" > /dev/null 2>&1 || true
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb logcat -c || true
demo_mode
if adb install app/build/outputs/apk/release/app-release.apk > "$OUT/release-install.txt" 2>&1; then
  if python3 .github/scripts/release_smoke.py "$OUT/release" > "$OUT/release_smoke.txt" 2>&1; then
    record release_apk_smoke PASS "$(tail -n 1 "$OUT/release_smoke.txt")"
  else
    record release_apk_smoke FAIL "$(tail -n 1 "$OUT/release_smoke.txt")"
  fi
else
  record release_apk_install FAIL "adb install of release APK failed"
fi
adb logcat -d > "$OUT/logcat-release.txt" 2>&1 || true

log "device tests finished with status $STATUS"
exit $STATUS
