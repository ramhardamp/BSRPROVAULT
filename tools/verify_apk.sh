#!/usr/bin/env bash
# Usage: tools/verify_apk.sh <apk> [expected-package] [require-release-signature: yes|no]
# Fails (exit 1) on anything that would make the APK "not installable": bad zip, unsigned / invalid signature,
# wrong applicationId, missing version info, or (for release) a debug-key signature.
set -euo pipefail
APK="${1:?apk path}"; EXPECT_PKG="${2:-com.babasitaram.pro}"; REL="${3:-no}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -n1)"
[ -n "$BT" ] || { echo "ERROR: Android build-tools not found (ANDROID_HOME=$SDK)"; exit 1; }
echo "== $APK  (build-tools: $(basename "$BT"))"
case "$APK" in *unsigned*) echo "ERROR: file name says unsigned"; exit 1;; esac
unzip -tq "$APK" >/dev/null && echo "OK   zip integrity"
BADGE="$("$BT/aapt2" dump badging "$APK")"
PKG="$(echo "$BADGE" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
VC="$(echo "$BADGE" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -n1)"
VN="$(echo "$BADGE" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n1)"
MINSDK="$(echo "$BADGE" | sed -n "s/^sdkVersion:'\([^']*\)'.*/\1/p")"
TGT="$(echo "$BADGE" | sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p")"
ABIS="$(echo "$BADGE" | sed -n "s/^native-code: //p")"
echo "package=$PKG versionCode=$VC versionName=$VN minSdk=$MINSDK targetSdk=$TGT abis=${ABIS:-none (pure Java/Kotlin → all devices)}"
[ "$PKG" = "$EXPECT_PKG" ] || { echo "ERROR: applicationId '$PKG' != '$EXPECT_PKG'"; exit 1; }
[ -n "$VC" ] && [ -n "$VN" ] || { echo "ERROR: version info missing"; exit 1; }
echo "$BADGE" | grep -q "^application-debuggable" && echo "NOTE debuggable=true (debug build)" || echo "OK   not debuggable"
OUT="$("$BT/apksigner" verify --verbose --print-certs "$APK" 2>&1)" || { echo "$OUT"; echo "ERROR: APK signature does NOT verify → installer would say 'App not installed'"; exit 1; }
echo "$OUT" | sed -n '1,12p'
echo "$OUT" | grep -q "Verifies" && echo "OK   signature verifies"
if [ "$REL" = "yes" ] && echo "$OUT" | grep -qi "CN=Android Debug"; then echo "ERROR: release APK is signed with the DEBUG key"; exit 1; fi
echo "SHA-256 cert: $(echo "$OUT" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n1)   (must stay identical across updates)"
echo "PASS"
