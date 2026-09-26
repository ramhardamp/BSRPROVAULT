# v6.8.0 — BSRPRO.Vault V3, export gate, signing/CI (merged from v6.7.9 + external research + handoff spec)
## Backup format
- NEW standard export/auto-backup: `BSRPRO.Vault` (V3): AES-256-GCM, PBKDF2-HMAC-SHA256 **600,000**, backup-domain KDF, random salt+IV per backup, authenticated header. See BSRPRO-VAULT-FORMAT-V3.md.
- Same format in Android, Chrome and Firefox (manual export AND extension auto-backup — previously the extension auto-backup used a different, JSON-wrapped format under the same file name).
- Legacy `.vaultbak` / `.bsrpro` / extension `vault_backup` wrapper: import only. No weaker-crypto fallback for V3. Import is all-or-nothing.
- Android: new `BsrVault.kt`, `ExportGate.kt`; `BackupManager.buildBsrProVault / buildAutoBackup`, strict `importBsrProVault`. Removed the reachable `.bsrpro` exporter.
## Security fixes
- **Extension export "verification" never worked**: it checked `window.VaultCrypto`, which is undefined (`VaultCrypto` is a top-level `const`), the TypeError was swallowed by `catch {}`, and export proceeded with ANY password. Now real, fail-closed verification.
- **Extension plaintext exports (Chrome/Bitwarden/LastPass CSV, KeePass XML, plain JSON) needed no Master Password at all.** Now required, with the plaintext warning.
- Export gate enforced at builder/writer level (Android + extensions), not only on the button.
- Removed unused `READ_USER_DICTIONARY` permission. (`SettingsActivity` stays exported: Android Autofill settings launches it via `android:settingsActivity`; it requires an unlocked vault.)
## Carried from v6.7.9
- `.vaultbak`/export truncation ("wt") fix + size verify; id-collision-safe import (`mergeFresh`); backup-password retry dialog; `@Synchronized` VaultManager; reset clears auto-backup pointer;
  lock guard on AddEdit/Backup; Xiaomi biometric (`BIOMETRIC_WEAK`); ANR-free change-master; generator fixes + `SecureClip`; Mi/other browsers in autofill browser list.
## Build / release
- Signing config (from external research) + GitHub-Secrets signing in CI; `tools/verify_apk.sh` (zip integrity, signature, applicationId, version, not-debug-signed); lint (report-only); Node tests of the real extension code run in CI.
- versionCode 20 / 6.8.0. Extensions 5.41.0.
## How it was verified
- Kotlin logic layer compiled against real Gson + android.jar and run on the JVM: 19 checks (gate, roundtrip, folders, unicode, wrong password, tamper, truncation, count mismatch, malformed entry, foreign origin).
- Node runs the real patched extension code (Chrome + Firefox): gate fail-closed, V3 roundtrip, tamper, legacy `.vaultbak`. Cross-tests: Kotlin→Node, Node→Kotlin, extension→Android import, legacy(original code)→Android.
- NOT verified here (no Android SDK/device): full Gradle build, UI screens, install, autofill, Xiaomi biometric. Run TEST-CHECKLIST-v6.8.0.md.
- `tests/compat/*` are older reference files that still mention `buildExtensionBackup`; they are not part of the Gradle build.
