#!/usr/bin/env bash
# Collects logs, test results, screenshots and the release APK, and force-pushes them
# to the `ci-results` branch so they can be inspected without downloading artifacts.
set -u
RUN=$1
SHA=$2
BUILD_RC=${3:-unknown}
mkdir -p ci-out/unit ci-out/apk
cp build.log ci-out/ 2>/dev/null || true
cp app/build/test-results/testDebugUnitTest/*.xml ci-out/unit/ 2>/dev/null || true
cp app/build/outputs/apk/release/app-release.apk "ci-out/apk/MoneyTracker-release.apk" 2>/dev/null || true
ls -la app/build/outputs/apk/*/ > ci-out/apk/listing.txt 2>&1 || true
echo "run=$RUN sha=$SHA gradle_rc=$BUILD_RC date=$(date -u +%FT%TZ)" > ci-out/RUN.txt
python3 .github/scripts/summarize.py ci-out "$BUILD_RC" > ci-out/SUMMARY.md 2>&1 || true
cd ci-out
git init -q
git checkout -q -b ci-results
git add -A
git -c user.name="ci" -c user.email="ci@users.noreply.github.com" commit -qm "CI run $RUN ($SHA)"
git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci-results
echo "Results published to branch ci-results"
