#!/usr/bin/env bash
# Every theme must be wired into ALL of its sites.
#
# This exists because the app has already shipped the bug it checks for. Adding
# a theme touches five separate places in two files, and missing one does not
# fail to compile -- it ships. The known failure modes:
#
#   * missing from appThemeOptions  -> invisible in the picker
#   * missing from toAppTheme()     -> silently falls back to Dark when restored
#   * missing from surfacePalettes  -> silently renders as Dark
#   * a light theme missing from isDarkSurface() -> white status-bar icons on a
#     white background: the clock and battery simply disappear
#
# Only the last one is visually obvious, and only on a device.
set -u
THEME="app/src/main/java/com/streamflow/ui/theme/Theme.kt"
fail=0

enum_line=$(grep -m1 '^enum class AppTheme' "$THEME")
themes=$(echo "$enum_line" | sed 's/.*{//; s/}.*//' | tr ',' '\n' | tr -d ' ' | grep -v '^$')

options=$(sed -n '/^val appThemeOptions/,/^)/p' "$THEME")
convert=$(sed -n '/^fun String.toAppTheme/,/^}/p' "$THEME")
palettes=$(sed -n '/^private val surfacePalettes/,/^)/p' "$THEME")
# The else-> target of toAppTheme(): covered without being named.
fallback=$(echo "$convert" | grep -m1 'else' | sed 's/.*AppTheme\.//; s/[^A-Z_].*//')
darksurf=$(sed -n '/^fun AppTheme.isDarkSurface/,/^}/p' "$THEME")

printf '%-12s %-8s %-9s %-9s %s\n' THEME PICKER RESTORE PALETTE NOTE
printf -- '------------------------------------------------------------\n'

for t in $themes; do
  note=""
  in_opts=no;  echo "$options"  | grep -q "\"$t\"" && in_opts=yes
  # A theme may be covered literally OR as the else-> fallback target. Counting
  # the fallback as missing would flag correct code, and a checker that cries
  # wolf gets ignored -- which is how a real one would then slip through.
  in_conv=no;  echo "$convert"  | grep -q "\"$t\"" && in_conv=yes
  [ "$t" = "$fallback" ] && in_conv=fallback
  in_pal=no;   echo "$palettes" | grep -q "AppTheme\.$t\b" && in_pal=yes

  # SYSTEM is deliberately none of these: it resolves to DARK or LIGHT at runtime.
  if [ "$t" = "SYSTEM" ]; then
    printf '%-12s %-8s %-9s %-9s %s\n' "$t" "$in_opts" "n/a" "n/a" "resolves at runtime"
    [ "$in_opts" = yes ] || { echo "  FAIL: SYSTEM must still appear in the picker"; fail=1; }
    continue
  fi

  [ "$in_opts" = yes ] || { note="$note missing-from-picker"; fail=1; }
  [ "$in_conv" = no ] && { note="$note missing-from-toAppTheme"; fail=1; }
  [ "$in_pal"  = yes ] || { note="$note missing-palette"; fail=1; }

  # A light-surface palette MUST be named in isDarkSurface()'s false branch.
  if echo "$palettes" | sed -n "/AppTheme\.$t to SurfacePalette(/,/),/p" | grep -q 'light = true'; then
    if echo "$darksurf" | grep -q "AppTheme\.$t\b"; then
      note="$note light(status-bar OK)"
    else
      note="$note LIGHT-BUT-NOT-IN-isDarkSurface"
      fail=1
    fi
  fi

  printf '%-12s %-8s %-9s %-9s %s\n' "$t" "$in_opts" "$in_conv" "$in_pal" "$note"
done

echo
if [ "$fail" -eq 0 ]; then
  echo "PASS - every theme is wired into the picker, restore, palette and status-bar rules"
else
  echo "FAIL - a theme is missing one of its wiring sites (see notes above)"
fi
exit $fail
