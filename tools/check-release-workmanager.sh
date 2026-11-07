#!/usr/bin/env bash
# Check the artifact, not source declarations: R8 may remove unused constructors.
set -euo pipefail
if [[ $# != 1 ]]; then
  printf 'Usage: %s APK\n' "$0" >&2
  exit 2
fi
apk="$1"
if [[ ! -f "$apk" ]]; then
  printf 'APK does not exist: %s\n' "$apk" >&2
  exit 2
fi
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
analyzer="${APKANALYZER:-}"
if [[ -z "$analyzer" && -n "$sdk_root" ]]; then
  analyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
fi
if [[ -z "$analyzer" ]]; then
  analyzer="$(command -v apkanalyzer || true)"
fi
if [[ ! -x "$analyzer" ]]; then
  printf 'Set APKANALYZER or ANDROID_SDK_ROOT to the installed Android SDK.\n' >&2
  exit 2
fi
status=0
for merger in androidx.work.OverwritingInputMerger androidx.work.ArrayCreatingInputMerger; do
  if ! code="$(JAVA_OPTS="${JAVA_OPTS:-} -Xmx256m" "$analyzer" dex code --class "$merger" "$apk")"; then
    printf 'FAIL: cannot inspect %s\n' "$merger" >&2
    status=1
  elif ! rg -q '^\.method .*public .*constructor <init>\(\)V' <<< "$code"; then
    printf 'FAIL: %s has no public no-argument constructor in this APK\n' "$merger" >&2
    status=1
  else
    printf 'PASS: %s preserves its reflective constructor\n' "$merger"
  fi
done
exit "$status"
