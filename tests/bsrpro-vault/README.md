# BSRPRO.Vault V3 tests
- `ext_export_gate_and_v3_test.js` — runs the REAL patched extension code (`Extensions/CHROME|FIREFOX/src/import-export.js`):
  export gate (no Master Password = no file, fail-closed), V3 roundtrip, wrong password, tamper, truncation, legacy `.vaultbak`.
  `node tests/bsrpro-vault/ext_export_gate_and_v3_test.js CHROME` (Node 20+, no dependencies). CI runs it for both browsers.
- `KtIntegration.kt` — JVM integration test for `BackupManager` / `BsrVault` / `ExportGate` (needs kotlinc, gson, android.jar,
  and tiny shims for android.util.Log/Base64 and android.os.SystemClock — see CHANGELOG-v6.8.0.md for how it was run).
- `bsrv3_core.reference.js` — the exact V3 core that is embedded in the extensions.
