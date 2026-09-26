# BSR Vault v6.7.3 — Changelog

## Issue 1 — Password Save
- Preserved `SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE` already present in v6.7.2.
- Expanded `onSaveRequest()` diagnostics for IDs, domain, package, vault state, existing/new decision, and write result.
- Locked vault now produces an explicit failure log and callback failure instead of silent handling.
- `VaultManager.add()` and `VaultManager.update()` now return a Boolean write result.
- Failed vault writes are rolled back in memory.
- No password/secret values are written to logs.

## Issue 2 — Password Edit
- Explicit ID-based existing-entry lookup.
- Login entries validate site, username and password.
- Update/add result is checked before finishing the Activity.
- Success logs and user-facing success toast added.
- Failure leaves the Activity open and shows `Save failed`.

## Issue 3 — Main Vault UI
- Entry cards use 8dp outer spacing and 12dp internal padding.
- Favorite and 40dp More buttons remain visible.
- Edit/delete/copy/favorite/share actions moved into the More overflow menu.
- Long press opens the same context menu.
- Strength bar remains inside the entry card.
- Share intentionally excludes the password.

## Issue 4 — Settings UI
- Settings reorganized into Security, Autofill, Data, Appearance, General and Danger Zone.
- Consistent 16dp content padding, dividers and row heights.
- Existing functionality/IDs preserved.
- About and Help dialogs added.

## Version
- versionCode: 12
- versionName: 6.7.3

## Protected areas
Chrome Autofill, field detection, browser/domain/eTLD+1 matching, appPackage matching, crypto, vault JSON format, login/unlock/biometric and backup format were not intentionally changed.
