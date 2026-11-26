#!/usr/bin/env bash
# Runs inside the emulator: install, launch, take screenshots (before/after scrolling), collect crash info.
PKG=com.tribalfs.oneuisample8
OUT=docs/smoke
mkdir -p $OUT
APK=$(ls sample-app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" || { echo "INSTALL FAILED"; exit 0; }
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 12
adb exec-out screencap -p > $OUT/1_launch.png || true
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
X=$((W/2)); Y1=$((H*3/4)); Y2=$((H/4))
adb shell input swipe $X $Y1 $X $Y2 400; sleep 1
adb exec-out screencap -p > $OUT/2_scroll_a_little.png || true
adb shell input swipe $X $Y1 $X $Y2 400; sleep 1
adb shell input swipe $X $Y1 $X $Y2 400; sleep 1
adb exec-out screencap -p > $OUT/3_scrolled_more.png || true
adb shell input swipe $X $Y2 $X $Y1 400; sleep 1
adb shell input swipe $X $Y2 $X $Y1 400; sleep 1
adb shell input swipe $X $Y2 $X $Y1 400; sleep 1
adb exec-out screencap -p > $OUT/4_back_to_top.png || true
adb shell wm size > $OUT/info.txt; adb shell getprop ro.build.version.sdk >> $OUT/info.txt
adb logcat -d -b crash > crash.txt || true
echo "=== PROCESS"; adb shell pidof $PKG || echo "PROCESS NOT RUNNING"
echo "=== CRASH BUFFER"; head -60 crash.txt
