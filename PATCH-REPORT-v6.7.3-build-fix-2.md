# BSR Vault v6.7.3 — Build Fix 2

## Root cause from GitHub Actions

GitHub Actions compiler error:

`SettingsActivity.kt:130:47 Unresolved reference: spinnerClipClear`

`SettingsActivity.kt` still initializes and configures `spinnerClipClear` for the Clipboard Clear setting, but the revised `activity_settings.xml` did not contain `@+id/spinnerClipClear`.

## Fix

Restored the `spinnerClipClear` Spinner to the SECURITY section of `activity_settings.xml`, preserving the existing Kotlin logic and AppPrefs behavior.

No crypto, vault format, Autofill matching, login/unlock, backup compatibility, or save-flow logic was changed.

## Verification

- `activity_settings.xml` XML parse: PASS.
- `spinnerClipClear` ID now exists in the layout.
- Local Gradle build could not be completed because this environment cannot download Gradle 8.2 from `services.gradle.org` (`UnknownHostException`).
- GitHub Actions is required for final compile verification.
