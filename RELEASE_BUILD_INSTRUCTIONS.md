# Release build & signing — why "App not installed" happens and how this repo avoids it
Typical causes: (1) **unsigned** release APK (`app-release-unsigned.apk`) — Android refuses it; (2) update signed with a **different key** than the installed app
(GitHub runners create a NEW debug key every run, so every CI debug APK has a different signature); (3) lower versionCode than installed;
(4) same package already installed from another source/key. `tools/verify_apk.sh` (run by CI) fails the build for (1) and prints the certificate SHA-256 for (2).

## One-time: create YOUR release key (keep it safe — losing it means users must uninstall to update)
```bash
keytool -genkeypair -v -keystore release-keystore.jks -alias babasitaram-release -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release-keystore.jks > keystore.b64        # macOS: base64 -i release-keystore.jks -o keystore.b64
```
GitHub → repo → Settings → Secrets and variables → Actions → New repository secret:
`KEYSTORE_BASE64` (content of keystore.b64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (babasitaram-release), `KEY_PASSWORD`.
Push → Actions builds **debug** always and **signed release** when the 4 secrets exist; both are verified (signature, package, version).

## Local
`cp key.properties.example key.properties` (fill in) → `./gradlew assembleRelease` → `tools/verify_apk.sh app/build/outputs/apk/release/app-release.apk com.babasitaram.pro yes`.
`key.properties` and `*.jks` are git-ignored. Install over an existing app only if it was signed with the same key; otherwise uninstall first (export a backup first!).
`minifyEnabled` stays false (not verified with R8 on a device).
