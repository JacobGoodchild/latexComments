#!/usr/bin/env bash
# Installs the BUILT plugin zip into a fresh, separate IntelliJ IDEA profile and starts it on Xvfb :99, opening $1.
# This checks what users get (the zip), not the development sandbox. Test-machine helper.
#   tools/run-zip.sh <project folder> [log file]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
ide="$(ls -d "$HOME"/.gradle/caches/*/transforms/*/transformed/idea-2026.1.5.1 | head -1)"
zip="$(ls -t "$root"/build/distributions/latexcomments-*.zip | head -1)"
profile="${ZIP_PROFILE:-/tmp/latexcomments-zip-profile}"
rm -rf "$profile"; mkdir -p "$profile/plugins" "$profile/config/options"
unzip -q "$zip" -d "$profile/plugins"
printf 'idea.config.path=%s/config\nidea.system.path=%s/system\nidea.plugins.path=%s/plugins\nidea.log.path=%s/log\n' "$profile" "$profile" "$profile" "$profile" > "$profile/idea.properties"
cp "$ide/bin/idea64.vmoptions" "$profile/idea.vmoptions"
printf '%s\n' -Didea.trust.all.projects=true -Dide.show.tips.on.startup.default.value=false -Djb.consents.confirmation.enabled=false \
  '-Djb.privacy.policy.text=<!--999.999-->' -Didea.fatal.error.notification=disabled >> "$profile/idea.vmoptions"
[ -n "${UISCALE:-}" ] && echo "-Dsun.java2d.uiScale=$UISCALE" >> "$profile/idea.vmoptions"
pgrep -x Xvfb >/dev/null || (Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp >/dev/null 2>&1 &)
pkill -f "$root/.intellijPlatform/sandbox" || true
pkill -f "idea.paths.selector|$profile" || true
sleep 2
DISPLAY=:99 IDEA_PROPERTIES="$profile/idea.properties" IDEA_VM_OPTIONS="$profile/idea.vmoptions" nohup "$ide/bin/idea.sh" "$1" > "${2:-/tmp/latexcomments-zip.log}" 2>&1 &
( for i in $(seq 1 120); do sleep 2
    for w in $(DISPLAY=:99 xdotool search --onlyvisible --name "." 2>/dev/null); do
      g=$(DISPLAY=:99 xdotool getwindowgeometry "$w" | grep -o '[0-9]*x[0-9]*'); [ "$g" = "1920x1080" ] && exit 0
      [ "${g%x*}" -gt 1000 ] 2>/dev/null && DISPLAY=:99 xdotool windowmove "$w" 0 0 windowsize "$w" 1920 1080
    done; done ) >/dev/null 2>&1 &
echo "started $zip in $profile"
