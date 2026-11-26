#!/usr/bin/env bash
# usage: inspect_sesl8.sh <aar dir> <out file>   -- public API of the SESL 8 classes we care about
AARS="$1"; OUT="$2"; : > "$OUT"
mkdir -p /tmp/s8jars
for a in "$AARS"/*.aar; do n=$(basename "$a" .aar); unzip -p "$a" classes.jar > /tmp/s8jars/$n.jar 2>/dev/null || true; done
CP=$(ls /tmp/s8jars/*.jar | tr '\n' ':')
for c in com.google.android.material.appbar.AppBarLayout com.google.android.material.appbar.CollapsingToolbarLayout \
         com.google.android.material.tabs.TabLayout com.google.android.material.bottomnavigation.BottomNavigationView \
         androidx.appcompat.graphics.drawable.SeslRecoilDrawable androidx.appcompat.widget.SeslToolbar; do
  echo "######## $c" >> "$OUT"
  javap -public -cp "$CP" "$c" >> "$OUT" 2>&1 || echo "(not found)" >> "$OUT"
done
echo "######## classes in com.google.android.material.oneui.*" >> "$OUT"
for j in /tmp/s8jars/*.jar; do unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -E 'material/oneui/|appbar/|tabs/' | grep -v '\$' ; done | sort -u >> "$OUT"
