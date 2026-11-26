#!/usr/bin/env bash
# Runs inside the emulator: install, launch, collect crash information.
PKG=com.tribalfs.oneuisample8
APK=$(ls sample-app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK" || { echo "INSTALL FAILED"; exit 0; }
adb logcat -c
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 20
adb exec-out screencap -p > shot1.png || true
adb logcat -d -b crash > crash.txt || true
adb logcat -d > full.txt || true
echo "=== PROCESS"
adb shell pidof $PKG || echo "PROCESS NOT RUNNING"
echo "=== CRASH BUFFER"
head -120 crash.txt
echo "=== FATAL EXCEPTION (full log)"
grep -n -A40 "FATAL EXCEPTION" full.txt | head -160
echo "=== Caused by"
grep -n "Caused by" full.txt | head -20
