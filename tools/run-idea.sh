#!/usr/bin/env bash
# Starts the sandbox IntelliJ IDEA (free tier) with the plugin on the Xvfb display :99, opening $1.
# Test-machine helper for UI screenshots (a throwaway machine with a virtual screen). Not needed on your own computer.
#   tools/run-idea.sh <project folder> [log file]      UISCALE=2 for a 200 % screen
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
pgrep -x Xvfb >/dev/null || (Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp >/dev/null 2>&1 &)
pkill -f "$root/.intellijPlatform/sandbox" || true
sleep 2
export DISPLAY=:99
cd "$root"
extra=()
[ -n "${UISCALE:-}" ] && extra+=(-PuiScale="$UISCALE")
[ -n "${OPENFILE:-}" ] && extra+=(-PopenFile="$OPENFILE")
nohup ./gradlew runIdea -PopenPath="$1" -PtestMachine "${extra[@]}" --console=plain > "${2:-/tmp/latexcomments-runidea.log}" 2>&1 &
# No window manager on Xvfb: once the IDE window exists, make it fill the screen.
( for i in $(seq 1 120); do sleep 2
    for w in $(xdotool search --onlyvisible --name "." 2>/dev/null); do
      g=$(xdotool getwindowgeometry "$w" | grep -o '[0-9]*x[0-9]*'); [ "$g" = "1920x1080" ] && exit 0
      [ "${g%x*}" -gt 1000 ] 2>/dev/null && xdotool windowmove "$w" 0 0 windowsize "$w" 1920 1080
    done; done ) >/dev/null 2>&1 &
echo "started"
