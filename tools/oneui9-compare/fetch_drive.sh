#!/usr/bin/env bash
# usage: fetch_drive.sh <file id> <out>
set -euo pipefail
id="$1"; out="$2"
page=$(mktemp)
curl -sL -o "$page" "https://drive.google.com/uc?export=download&id=$id"
uuid=$(grep -o 'name="uuid" value="[^"]*"' "$page" | sed 's/.*value="//;s/"//' || true)
curl -sL -o "$out" "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t&uuid=$uuid"
file "$out" | cut -c1-80
