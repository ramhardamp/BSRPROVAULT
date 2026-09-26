# BSR Vault v6.7.6 Patch Report

## Source verification
Repository: ramhardamp/PasswordManager-App
Branch: main
Version: 6.7.6 / versionCode 15
Device evidence supplied: Samsung SM-M346B, Android 16, diagnostics 22 Sept 11:25.

## Exact findings

### BUG 1 — Main search bar
File: app/src/main/res/layout/activity_main.xml
Finding: Search was already correctly placed inside AppBarLayout, before category chips, but its height was wrap_content and spacing was minimal. This did not match the polished UI target.
Fix: fixed 58dp height and small top/bottom spacing. No search behavior changed.

### BUG 2 — Theme
Files: app/src/main/java/com/babasitaram/pro/SettingsActivity.kt; app/src/main/java/com/babasitaram/pro/BSRApp.kt; res/values/themes.xml; res/values-night/themes.xml; AppPrefs.kt.
Finding: Light/Dark/System persistence and AppCompat DayNight wiring are present. Settings calls AppCompatDelegate.setDefaultNightMode() and the app applies the persisted mode at startup. No source defect was found that justified changing the theme architecture.
Action: retained the working DayNight implementation rather than introducing a second theme system. Android recommends DayNight plus MODE_NIGHT_NO/YES/FOLLOW_SYSTEM for in-app theme selection.

### BUG 3 — Backup restore
Files: BackupActivity.kt, BackupManager.kt, AutoBackup.kt.
Finding: .bsrpro restore path already performs signature/app/magic/origin/decryption checks and merges only deduplicated entries. The auto-backup folder restore path already detects the managed backup and offers Restore/Merge.
Fix: hardened selected-file reading and managed-folder file handling so unreadable/empty files fail explicitly and folder/file type collisions do not crash the writer.

### BUG 4 — Import
File: BackupActivity.kt.
Finding: import supports .bsrpro, extension backup, CSV and KeePass XML through BackupManager. The selected URI reader previously returned an empty string when openInputStream() returned null, producing a vague downstream failure.
Fix: throw a clear I/O error for unreadable/empty selected files. Existing verification/decryption logic remains unchanged.

### BUG 5 — Auto-backup folder crash
Files: SettingsActivity.kt, AutoBackup.kt, AppPrefs.kt.
Finding: SAF tree permission persistence already existed, but the writer assumed the managed filename was a writable regular file. On some providers a stale URI or name collision can invalidate that assumption.
Fix: validate the selected tree/file, delete a same-named non-file entry when possible, recreate the managed backup file, and fall back from mode "wt" to "w" if the provider rejects the first mode. Existing folder permission flow is retained.

### BUG 6 — Direct password/entry click crash
File: MainActivity.kt.
Finding: the entry-detail path could throw from optional/malformed TOTP/custom-field/history data. The list item handler now catches unexpected detail-render exceptions instead of taking down the Activity. History/custom-field access is defensive.
Fix is UI-only; vault/crypto data is not changed.

### BUG 7 — "MPT khili"
File: AddEditActivity.kt.
Finding: the current source contains no "MPT khili" string. The current successful save messages are "Entry updated" for edits and "Password saved" for new entries. This symptom is therefore consistent with an older/stale APK or a message outside the current source, not a current source literal.
Action: no fake text replacement was made.

### BUG 8 — App Picker app name
File: AppPickerDialog.kt.
Finding: current code already displays appName above packageName, but label resolution depended on getApplicationLabel(). 
Fix: prefer ResolveInfo.loadLabel(), then PackageManager.getApplicationLabel(), then package suffix only as a final fallback. Search remains by both app name and package name.

## Security change

File: BSRAutofillService.kt
The service now immediately returns null for package com.babasitaram.pro. This prevents BSR Vault's own Add/Edit fields from receiving its own vault suggestions. Chrome/JioPOS matching logic is untouched.

## Auto-backup behavior
When a folder is selected, its SAF tree URI is persisted. If the managed encrypted backup already exists, the user is offered Restore/Merge. If they keep the current vault, or after a successful restore/merge, the same folder is written with the current encrypted vault. Subsequent successful vault saves schedule the same encrypted backup.

## Intentionally untouched
- AES-GCM vault encryption
- PBKDF2 vault derivation
- encrypted vault JSON format
- Master Password verification
- .bsrpro encryption/format
- Chrome/browser domain and eTLD+1 matching
- native appPackage matching
- Autofill field detection
- SaveInfo / password-save flow

## Verification status
Static source inspection: PASS for requested files and constraints.
GitHub Actions build: must be checked after these commits; no local build claim is made here.
Device regression: requires installing the v6.7.6 artifact and running the checklist.
