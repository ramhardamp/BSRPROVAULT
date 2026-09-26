# BSR Vault v6.7.5 — Master Password Export Verification Patch

## Requirement
Every current sensitive/plaintext export must verify the existing Master Password before the document picker/export starts. The encrypted `.bsrpro` backup flow remains unchanged.

## Files changed
- `app/src/main/java/com/babasitaram/pro/ExportSecurity.kt` — centralized reusable verification gate.
- `app/src/main/java/com/babasitaram/pro/BackupActivity.kt` — CSV and `.vaultbak` exports wrapped with the verification gate.
- `app/src/main/res/layout/dialog_export_master.xml` — Material outlined password dialog content.
- `app/src/main/res/drawable/dialog_export_bg.xml` — rounded 12dp dialog surface.
- `app/build.gradle` — version 6.7.5 / versionCode 14.
- `CHANGELOG-v6.7.5.md` — export security entry.

## Current export coverage
- CSV — Extension / Vault format: protected.
- CSV — Chrome / Google / Bitwarden format: protected.
- `.vaultbak` extension export: protected.
- `.bsrpro` encrypted backup: intentionally unchanged.
- Standalone JSON export: no current Android export button/path was found in the source; `ExportSecurity.requireMasterPassword(...)` is reusable for a future JSON/sensitive export.

## Verification behavior
1. User clicks a protected export.
2. `Confirm Master Password` dialog opens.
3. Existing `VaultManager.verifyMaster(...)` is used; no new crypto/verifier is introduced.
4. Correct password clears failure state, dismisses the dialog, and continues the existing export flow.
5. First/second wrong password: inline error + Toast; dialog remains open.
6. Third wrong password: dialog closes and export verification is rate-limited for 30 seconds.
7. Cancel: no file picker and no export.
8. Password field is cleared after each attempt and on dialog dismissal. No password is logged or persisted by the export gate.

## Compatibility
- AES-GCM unchanged.
- PBKDF2 unchanged.
- Vault JSON format unchanged.
- Existing `.bsrpro` backup format unchanged.
- Autofill detection/matching unchanged.
- Chrome/JioPOS paths unchanged.
- Add/Edit, lock management and theme fixes remain intact.

## Build status
The source changes are committed to the connected GitHub repository. GitHub Actions should perform compiler/build verification; local build in the previous environment was blocked by DNS resolution for `services.gradle.org`.