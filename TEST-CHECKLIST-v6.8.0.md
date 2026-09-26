# Device test checklist (v6.8.0) — run after the GitHub build
Install: uninstall nothing on your production phone first. Test phone: install the **signed release** (or the debug APK if no secrets).
1. Create / save / edit / delete a login. 2. App Picker → link an app → Autofill in that app. 3. Browser/domain Autofill (Chrome + Mi Browser).
4. Lock → unlock (Xiaomi: fingerprint/face works, not only PIN/pattern; Samsung also).
5. Backup → Export Backup → Master Password asked → file **BSRPRO.Vault**. Wrong Master Password → no file is created.
6. Export the same name twice (overwrite) → import the second file into the Chrome/Firefox extension.
7. Extension → Export "BSRPRO.Vault" → import in the APK. Extension plaintext exports (Chrome CSV etc.) now ask the Master Password + show the warning.
8. Import with wrong password → nothing imported. Corrupt the file (delete a few characters) → nothing imported.
9. Legacy `.vaultbak` from an old extension/APK still imports. Legacy `.bsrpro` still imports.
10. CSV export (both) → Master Password asked first. 11. Settings → Auto-backup folder → `BSRPRO.Vault` appears; old `.bsrpro` is removed.
12. Change Master Password (no freeze), then import an old backup → "Backup ka password" dialog.
13. Vault reset → new setup → re-pick auto-backup folder → Restore/Merge is offered (old backup not overwritten).
14. App restart; update install over the previous build (same signing key) → "App not installed" must NOT appear.
