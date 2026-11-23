#!/usr/bin/env bash
# Screenshot of the Xvfb display: tools/shot.sh out.png [scale%] [crop WxH+X+Y]
# Test-machine helper. Not needed on your own computer.
set -euo pipefail
out="$1"; scale="${2:-100}"; crop="${3:-}"
tmp="$(mktemp --suffix=.png)"
import -display "${DISPLAY:-:99}" -window root "$tmp"
if [ -n "$crop" ]; then convert "$tmp" -crop "$crop" +repage -resize "$scale%" "$out"; else convert "$tmp" -resize "$scale%" "$out"; fi
rm -f "$tmp"
