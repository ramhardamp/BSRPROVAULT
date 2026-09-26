# BSRPRO.Vault — backup format V3 (APK · Chrome · Firefox)

Current standard backup file: **`BSRPRO.Vault`**. Legacy `.vaultbak` (and old `.bsrpro`) stay **import-only**.

```
file    = Base64( header(38 bytes) || AES-256-GCM ciphertext || tag(16) )
header  = "BSRV"(4) | 0x03 version | 0x01 KDF=PBKDF2-HMAC-SHA256 | iterations u32 BE (=600000) | salt(16) | iv(12)
AAD     = the entire 38-byte header (version, KDF id, iterations, salt, iv are all authenticated)
KDF     = PBKDF2-HMAC-SHA256( UTF-8(master), "BSRPRO.Vault.v3.backup\0" || salt, 600000 ) -> 32 bytes   (backup-domain-specific)
plain   = UTF-8 JSON { "_bsrOrigin":"BABASITARAMPro:<platform>", "format":"BSRPRO.Vault", "version":3, "ts":ms, "count":N, "data":"<inner>" }
inner   = entries JSON: array, or {version, app, count, entries[], folders[]}  (same schema the extension/APK already use)
```
Every V3 file starts with the 8 characters `QlNSVgMB` (that is how importers detect it).

Rules
- New random salt **and** new random IV for every backup. Never a static IV.
- V3 is decrypted with V3 rules only: iterations must equal 600000 — **no fallback** to 310k/100k, so a crafted header cannot force weaker crypto.
- Import is all-or-nothing: wrong password, GCM tag failure, truncated/tampered header, foreign `_bsrOrigin`, wrong `format`/`version`,
  `count` ≠ number of entries, or any non-object entry ⇒ the whole import fails, nothing is written.
- Plaintext exists only in memory; no plaintext backup is ever written to disk. Auto-backup uses the same encrypted format, only while unlocked;
  the master password is never logged or stored in a file.
- Legacy import stays: `.vaultbak` base64(AES-GCM 310k/100k/600k), the extension auto-backup `{"vault_backup":true,...}` wrapper, `.bsrpro` JSON.

Implementations: `app/.../BsrVault.kt` (Android, pure JVM) · `Extensions/*/src/import-export.js`, `src/background.js`, `setup.js` (`_BSRV3`).
Verified both directions (Kotlin ⇄ Node) including a Unicode master password.

## Export gate (Master Password before ANY export)
Verification is enforced at the builder/writer layer, not just on the button:
- Android: `ExportGate.require()` inside `BackupManager.buildBsrProVault / exportCsv / exportChromeCsv`; grant is issued only by `ExportSecurity` after `verifyMaster()` and revoked after the write.
- Extensions: `_ExportGate` — `downloadFile`, every `export*` builder and `encryptBackup` throw without a fresh grant; `verifyAndGrant()` is fail-closed.
- Plaintext exports (CSV/Chrome/Bitwarden/LastPass/KeePass XML/plain JSON): Master Password first, with the warning
  "यह export encrypted नहीं है और इसमें आपके passwords हो सकते हैं।"
