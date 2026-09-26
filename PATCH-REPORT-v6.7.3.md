# BSR Vault v6.7.3 — Patch Report

## Verification note
The attached v6.7.2 project was inspected directly. An important correction to the earlier analysis: the v6.7.2 source already contains `SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE` at `BSRAutofillService.kt:200`. Therefore v6.7.3 does not add a duplicate flag; it preserves that configuration and fixes the surrounding save diagnostics/write-result handling.

## Issue 1 — Password Save

### Confirmed source
- `BSRAutofillService.kt:192-201`: SaveInfo is already built with `SAVE_DATA_TYPE_PASSWORD`, password IDs, optional username IDs, and `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE`.
- `BSRAutofillService.kt:291-363`: `onSaveRequest()` previously returned success after calling add/update without checking whether the vault write actually succeeded and had only minimal logging.
- `VaultManager.kt:229-240`: `savePw()` previously returned no status and swallowed write failures.
- `VaultManager.kt:261-272`: `add()` previously returned no status.
- `VaultManager.kt:294-313`: `update()` previously returned no status.

### v6.7.3 changes
- `BSRAutofillService.kt:291-363`: structured save diagnostics and explicit success/failure callback handling.
- `VaultManager.kt:229-240`: `savePw()` now returns Boolean.
- `VaultManager.kt:261-272`: `add()` now returns Boolean and rolls back the in-memory add if persistence fails.
- `VaultManager.kt:294-316`: `update()` now returns Boolean and restores the previous entry if persistence fails.

## Issue 2 — Password Edit

### Confirmed source
- `AddEditActivity.kt:198-246`: previous save path always called add/update and then showed `Saved!` + `finish()` without checking the write result.

### v6.7.3 changes
- `AddEditActivity.kt:198-279`: explicit edit ID lookup, login validation, save START/SUCCESS/FAIL logs, Boolean result check, conditional finish.

## Issue 3 — Main Vault UI

### Confirmed source
- `item_password.xml` previously exposed five action buttons in one row: favorite, copy username, copy password, edit, delete.
- `MainActivity.kt` previously wired all five buttons directly and long press directly opened Edit.

### v6.7.3 changes
- `item_password.xml`: 40dp visible action targets, 8dp spacing, 12dp card padding, 8dp outer card spacing, More overflow button.
- `MainActivity.kt`: copy/edit/delete/favorite/share moved to PopupMenu; long press opens the same menu.
- Password is deliberately excluded from Share content.

## Issue 4 — Settings

### Confirmed source
- `activity_settings.xml` previously had Security, Privacy and Account sections, with most functions presented as large standalone buttons.
- `SettingsActivity.kt` uses stable IDs for existing controls; those IDs were preserved.

### v6.7.3 changes
- `activity_settings.xml`: Security, Autofill, Data, Appearance, General and Danger Zone sections.
- 16dp content padding, 56-60dp rows and divider lines.
- Existing button IDs preserved.
- Added About/Help buttons and simple dialogs.

## Version
- `app/build.gradle:14` versionCode 12
- `app/build.gradle:15` versionName `6.7.3`

## Build verification
Local Gradle build was attempted with `./gradlew clean assembleDebug --stacktrace`, but the environment could not download Gradle 8.2 because `services.gradle.org` was unreachable (`UnknownHostException`). Therefore BUILD SUCCESSFUL is not claimed.

The GitHub Actions workflow was updated to run `./gradlew clean assembleDebug --stacktrace` and upload the v6.7.3 debug APK artifact.
