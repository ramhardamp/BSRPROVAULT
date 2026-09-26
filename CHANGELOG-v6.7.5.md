# BSR Vault v6.7.5 — Pro Hardening

## Included
- Explicit **Lock Vault Now** action from Main and Settings.
- Lock immediately clears any BSR-owned clipboard secret before leaving the vault.
- Clipboard auto-clear is ownership-aware.
- Android 13+ copied secrets are marked sensitive.
- Autofill Diagnostics reports non-secret security configuration state.
- GitHub Actions discovers the nested Gradle project automatically.
- Version: `6.7.5` / versionCode `14`.

## Export security hardening
- Added centralized `ExportSecurity.requireMasterPassword(...)` for sensitive/plaintext exports.
- CSV Vault/Extension export requires Master Password verification before the document picker.
- CSV Chrome/Google/Bitwarden/universal export requires Master Password verification before the document picker.
- `.vaultbak` extension export requires Master Password verification before file creation.
- Existing `.bsrpro` encrypted backup flow is unchanged.
- Wrong password: first two attempts keep the dialog open; the third closes it and starts a 30-second export-only cooldown.
- Password input is cleared after attempts and dismissal; no password is logged or persisted by the export gate.
- The helper is reusable for future JSON/sensitive exports.

## Preserved
- AES-GCM encrypted vault format.
- PBKDF2 vault key derivation.
- Existing VaultManager data model and backup compatibility.
- Existing Autofill field detection, app-package matching and browser domain/eTLD+1 matching.
- Existing login, biometric, App Picker and save/update flows.
- Existing Light/Dark/System theme implementation.

## Verification
GitHub Actions build and real-device regression are required before calling this release verified.