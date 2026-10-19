#!/bin/bash
# Takes the Play Store screenshots on the running Android emulator (emulator-5554): five scenes in
# en, hi and ta, as raw 1080x2400 captures. Fit them to 1080x1920 afterwards with
#   /path/to/venv/bin/python docs/play/make_assets.py <raw-dir>
#
# Usage: docs/play/take_screenshots.sh <release-apk> [raw-dir]
# The emulator must already be running (any API 33+ image). The script installs the APK, makes
# LinkGuard the default browser, turns on the status-bar demo mode, and opens fictional .example
# links that trigger each warning on-device. Scene 4 (the community "reported by N users" warning)
# needs 3+ reports for parttime-job-offer.example on the server; set SKIP_COMMUNITY=1 to leave it out.
set -u
APK=${1:?release apk}
RAW=${2:-/tmp/lg-raw}
export ANDROID_ADB_SERVER_PORT=${ANDROID_ADB_SERVER_PORT:-5038} ADB_LIBUSB=1
ADB=~/Library/Android/sdk/platform-tools/adb
E=emulator-5554
PKG=com.dwarpallabs.linkguard
a() { for i in 1 2 3 4 5 6; do $ADB -s $E "$@" 2>/dev/null && return 0; sleep 2; $ADB start-server >/dev/null 2>&1; done; return 1; }
rm -rf "$RAW"; mkdir -p "$RAW"
$ADB start-server >/dev/null 2>&1
for i in $(seq 1 150); do [ "$(a shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
a shell cmd uimode night no >/dev/null
a shell settings put system font_scale 1.0
a uninstall $PKG >/dev/null 2>&1
echo "install: $(a install "$APK" 2>&1 | tail -1)"
a shell cmd role add-role-holder android.app.role.BROWSER $PKG >/dev/null 2>&1
echo "role: $(a shell cmd role get-role-holders android.app.role.BROWSER)"
# Warm up once so the allowlist download and the server connection are ready.
a shell am start -n $PKG/.MainActivity >/dev/null; sleep 8
a shell settings put global sysui_demo_allowed 1
d() { a shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
d enter; d clock -e hhmm 1000; d network -e wifi show -e level 4; d network -e mobile show -e level 4 -e datatype none
d battery -e level 100 -e plugged false; d notifications -e visible false
open() { a shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "$1" >/dev/null; }
shot() { sleep 1; a exec-out screencap -p > "$RAW/$1.png"; echo "  $1"; }
for l in en hi ta; do
  echo "== $l"
  a shell cmd locale set-app-locales $PKG --locales $l
  a shell am force-stop $PKG
  a shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 3; shot "1-home_$l"
  a shell input swipe 540 1700 540 500 400; sleep 1; a shell input swipe 540 1700 540 900 400; sleep 2; shot "5-checks_$l"
  a shell input keyevent HOME; sleep 1; open "https://hdfcbank-kyc-update.example/verify"; sleep 4; shot "2-brand-warning_$l"
  a shell input keyevent HOME; sleep 1; open "https://free-recharge-offer.example/Recharge.apk"; sleep 4; shot "3-app-download-warning_$l"
  if [ "${SKIP_COMMUNITY:-0}" != 1 ]; then
    a shell input keyevent HOME; sleep 1; open "https://parttime-job-offer.example/apply"; sleep 5; shot "4-community-warning_$l"
  fi
  a shell input keyevent HOME
done
a shell cmd locale set-app-locales $PKG --locales en
d exit
echo "captures: $(ls "$RAW" | wc -l | tr -d ' ') in $RAW"
