# BSR VAULT — FINAL MASTER ROADMAP & AI/DEVELOPER HANDOFF

**Project:** BabaSitaRam Pro / BSR Vault  
**Current target:** v6.7 stabilization  
**Document type:** Living roadmap, implementation status, bug register, acceptance checklist, and autonomous AI handoff specification.

---

## 0. इस फाइल का उद्देश्य

यह `roadmap.md` केवल future ideas की list नहीं है। यह BSR Vault का **living project control document** है।

कोई भी AI/developer इस फाइल को पढ़कर:

1. अभी तक क्या काम हुआ है, समझ सके।
2. कौन-सा काम अधूरा है, पहचान सके।
3. कौन-से bugs reproduce/fix करने हैं, समझ सके।
4. किन files को बदलना है और किन्हें नहीं छूना है, जान सके।
5. एक phase पूरा करके status update कर सके।
6. अगला pending task स्वतः चुन सके।
7. हर बदलाव के बाद build/test/verification कर सके।

> **महत्वपूर्ण:** जहाँ वास्तविक source code, device Logcat या verified build evidence उपलब्ध नहीं है, वहाँ status को `UNKNOWN`, `BLOCKED` या `NEEDS_VERIFICATION` रखा गया है। AI को अनुमान लगाकर किसी काम को `DONE` नहीं करना है।

---

# 1. STATUS SYSTEM — HIGHLIGHT कैसे करना है

इस document में status के लिए नीचे दिए गए markers का प्रयोग करें।

| Marker | Meaning | उपयोग |
|---|---|---|
| `🟩 DONE` | काम पूरा और test/verification से confirmed | पूर्ण काम |
| `🟨 PARTIAL` | कुछ हिस्सा काम करता है, कुछ बाकी है | अधूरा implementation |
| `🟥 BUG` | काम मौजूद है लेकिन defect है | crash, wrong behavior |
| `🟦 IN PROGRESS` | developer अभी काम कर रहा है | current task |
| `⬜ TODO` | अभी शुरू नहीं हुआ | pending task |
| `🟪 FUTURE` | future premium phase | v6.7 के बाद |
| `⚫ DO NOT ADD` | इस phase में नहीं करना | protected architecture |
| `⛔ BLOCKED` | evidence/access/dependency के कारण आगे नहीं बढ़ सकता | missing Logcat, unavailable build logs |
| `❔ NEEDS VERIFICATION` | source में होने का संकेत है, लेकिन real device test नहीं हुआ | verification required |

### AI के लिए status नियम

AI को किसी task को `🟩 DONE` करने से पहले कम-से-कम इनमें से उचित evidence देना होगा:

- source code review
- successful build
- installed APK test
- device test
- regression test result
- screenshot/video/Logcat
- backup/restore verification

सिर्फ “code लिख दिया” का अर्थ `DONE` नहीं है।

---

# 2. CURRENT PROJECT STATUS SUMMARY

## 2.1 Confirmed/Reported Progress

| Area | Status | Current information |
|---|---|---|
| APK build | 🟨 PARTIAL / NEEDS VERIFICATION | User reported that APK is building; complete clean release verification is still required |
| In-app Autofill | 🟨 PARTIAL | User reported that Autofill inside the app is appearing/working |
| Chrome/browser Autofill | 🟥 BUG / NEEDS INVESTIGATION | Chrome website fields do not currently show BSR Vault suggestions |
| Password Autofill/login | 🟨 PARTIAL | Password autofill/login reportedly works in some contexts |
| Android save-password popup | 🟥 BUG / NEEDS INVESTIGATION | Save-password popup is not appearing |
| Settings screen | 🟥 BUG / NEEDS INVESTIGATION | Expected Password Manager-like Settings behavior is not correct/visible |
| Edit password entry | 🟥 BUG / HIGH PRIORITY | App closes/crashes when editing a password entry |
| App Picker visibility | 🟥 BUG / NEEDS VERIFICATION | App Picker has appeared on screens/types where it should be hidden |
| App Picker feature | 🟨 PARTIAL | Package-linking feature was implemented/planned, but full edit/highlight/autofill regression is not confirmed |
| v6.7 full regression | ⬜ TODO | Must be performed after each fix |
| Clean release build | ❔ NEEDS VERIFICATION | Local build validation was blocked by Gradle download/network limitations; CI detailed compiler log is required if build fails |

---

# 3. PROJECT RULES — NON-NEGOTIABLE

## 3.1 Protected architecture

For v6.7, do not casually rewrite or replace:

- `VaultManager` architecture
- Existing `PasswordEntry` architecture
- Existing encrypted vault JSON format
- Existing AES-GCM encryption
- Existing PBKDF2/key derivation flow
- Existing login/unlock flow
- Existing biometric unlock flow
- Existing Autofill field detection
- Existing browser/domain/eTLD+1 matching
- Existing password-history behavior
- Existing backup/restore compatibility
- Existing working screens unrelated to the bug being fixed

## 3.2 Approved limited changes

The following targeted changes are allowed:

- `SettingsActivity` manifest/exported configuration
- Settings lock gate
- `autofill_service_config.xml` settings activity reference
- App Picker visibility and package-linking UI
- Defensive edit-entry loading
- SaveInfo/save-request handling
- Chrome integration guidance/status
- Application-level auto-lock lifecycle
- Dynamic version display
- Tests, diagnostics, logging safeguards, and documentation

---

# 4. PRIORITY ORDER

The AI/developer must follow this order:

1. 🟥 Fix edit-entry crash.
2. 🟥 Fix SettingsActivity opening and lock protection.
3. 🟥 Fix App Picker visibility by entry type.
4. 🟨 Verify App Picker save/edit/highlight behavior.
5. 🟨 Verify Autofill matching precedence.
6. 🟥 Fix SaveInfo and save-password lifecycle.
7. 🟨 Implement/verify application-level auto-lock.
8. 🟥 Test Chrome third-party Autofill configuration.
9. 🟨 Run complete regression suite.
10. 🟩 Mark v6.7 release-ready only after evidence exists.
11. 🟪 Start premium roadmap phases.

---

# 5. BUG REGISTER

## BUG-001 — Edit Password Entry Causes App Close

**Status:** 🟥 BUG — HIGH PRIORITY  
**Priority:** P0  
**Affected area:** `AddEditActivity`, entry loading, `VaultManager.getById()`, Kotlin `copy()` flow.

### User-visible symptom

When the user edits a saved password entry, the application closes/crashes.

### Known information

- The project uses a `PasswordEntry` data class and Kotlin `copy()` style updates.
- `PasswordEntry` is located in `VaultManager.kt`, not necessarily in a separate file.
- Exact crash cause is not confirmed without Android Logcat.
- A generic Gradle/Kotlin compilation stack trace is not evidence of this runtime crash.

### Required defensive behavior

1. If edit mode has no valid `entryId`:
   - show a safe message
   - call `finish()`
   - do not dereference the missing ID

2. If `VaultManager.getById(entryId)` returns null:
   - show a safe message
   - call `finish()`
   - do not continue with empty/null data

3. Wrap entry loading in safe error handling:
   - catch expected parsing/storage exceptions
   - log only non-sensitive diagnostic information
   - never log passwords, master password, decrypted vault JSON, or keys

4. Check critical views before use:
   - missing view should fail safely
   - do not force unwrap a missing view

5. Preserve existing immutable `val` fields and `copy()` behavior.

6. Verify all entry types:
   - login
   - card
   - identity
   - note
   - old entry without `appPackage`
   - entry with `appPackage`
   - entry with null optional fields

### Required evidence

- Device Logcat containing `FATAL EXCEPTION`
- First meaningful `Caused by:`
- File name and line number
- Reproduction steps
- Fixed build installed on device
- Edit regression test passed

### Acceptance criteria

- [ ] Editing login does not crash
- [ ] Editing card does not crash
- [ ] Editing identity does not crash
- [ ] Editing note does not crash
- [ ] Editing old entries does not crash
- [ ] Editing entries with app link does not crash
- [ ] Cancel edit works
- [ ] Save without changes works
- [ ] Password change preserves entry ID
- [ ] No secret data appears in logs

---

## BUG-002 — SettingsActivity Does Not Open Correctly

**Status:** 🟥 BUG — HIGH PRIORITY  
**Priority:** P0  
**Affected area:** `AndroidManifest.xml`, `SettingsActivity`, Autofill service metadata.

### Required manifest configuration

```xml
<activity
    android:name=".SettingsActivity"
    android:exported="true" />
```

### Required Autofill metadata configuration

The Autofill service metadata must point to the actual Settings screen:

```xml
android:settingsActivity="com.babasitaram.pro.SettingsActivity"
```

Do not point it to `LoginActivity` if the real settings screen is `SettingsActivity`.

### Required lock gate

At the beginning of `SettingsActivity.onCreate()`:

```kotlin
if (!VaultManager.isUnlocked) {
    startActivity(Intent(this, LoginActivity::class.java))
    return
}
```

The implementation must avoid loops and must allow the user to return to Settings after successful unlock.

### Acceptance criteria

- [ ] Settings opens from the app
- [ ] Settings opens from Android Autofill settings where supported
- [ ] Locked vault cannot expose sensitive Settings content
- [ ] User is redirected to LoginActivity when locked
- [ ] Successful unlock returns to Settings
- [ ] No login/settings infinite loop
- [ ] Settings version is shown correctly
- [ ] Autofill setup button works

---

## BUG-003 — App Picker Appears on Wrong Entry Types

**Status:** 🟥 BUG  
**Priority:** P1  
**Affected area:** `activity_add_edit.xml`, `AddEditActivity.applyType()`.

### Required exact visibility logic

```kotlin
val isLogin = type == "login"

findViewById<View>(R.id.appPickerBlock)?.visibility =
    if (isLogin) View.VISIBLE else View.GONE
```

### Required behavior

| Entry type | App Picker |
|---|---|
| Login | Visible |
| Card | Hidden |
| Identity | Hidden |
| Note | Hidden |
| Other non-login type | Hidden |

### Acceptance criteria

- [ ] New login shows App Picker
- [ ] Edit login shows App Picker
- [ ] New card hides App Picker
- [ ] Edit card hides App Picker
- [ ] Identity hides App Picker
- [ ] Note hides App Picker
- [ ] Switching type dynamically updates visibility
- [ ] Hidden App Picker does not save stale package values into non-login entries

---

## BUG-004 — App Picker Save/Edit/Highlight Behavior Incomplete

**Status:** 🟨 PARTIAL / NEEDS VERIFICATION  
**Priority:** P1  
**Affected area:** `AppPickerDialog`, `AddEditActivity`, `PasswordEntry`.

### Expected fields

```kotlin
val appPackage: String? = null
val appName: String? = null
```

Example:

```text
appName = WhatsApp
appPackage = com.whatsapp
```

### Picker must display

- app icon
- app name
- package name
- search
- selected/highlighted state

### Required behavior

- User selects an installed app.
- Package name is stored with the login entry.
- Editing the entry shows the previously selected app highlighted.
- Clearing the selection removes both package and name.
- Old entries without app fields remain valid.
- App Picker must not appear for cards, notes, or identity entries.

### Acceptance criteria

- [ ] WhatsApp can be selected
- [ ] `com.whatsapp` is saved correctly
- [ ] Reopening edit shows WhatsApp selected
- [ ] Changing selection updates package and name
- [ ] Clear selection works
- [ ] Old JSON entries load safely
- [ ] App icon failure does not crash the app
- [ ] Missing app label falls back safely to package suffix
- [ ] Icon dimensions use dp, not raw pixels
- [ ] Dark theme text remains readable

---

## BUG-005 — Chrome Autofill Suggestions Do Not Appear

**Status:** 🟥 BUG / ENVIRONMENT + IMPLEMENTATION INVESTIGATION  
**Priority:** P0  
**Affected area:** Android Autofill integration, Chrome settings, domain detection, provider selection.

### Important distinction

Chrome may use its own password manager/autofill path. BSR Vault will not necessarily receive Android third-party Autofill requests unless the relevant Chrome setting allows another Autofill provider.

### Required test setup

1. Open Chrome.
2. Open:

```text
chrome://settings/autofill
```

3. Verify the available option for using another/third-party Autofill service.
4. Enable the relevant option if present.
5. Select BSR Vault as the Android Autofill provider if Android requests it.
6. Restart Chrome.
7. Test on a real login page.

### Required BSR Vault Settings feature

Add a button:

```text
Open Chrome Autofill Settings
```

If no direct intent is reliable across devices, show clear manual instructions.

### Browser matching rule

For Chrome:

```text
package = com.android.chrome
```

must not override browser domain matching.

Example:

```text
Chrome + github.com
```

must match a GitHub domain entry, not an entry linked to WhatsApp or another native app.

### Required diagnostics

Without exposing secrets, record:

- detected package name
- detected web domain
- whether browser mode was detected
- matching mode: `DOMAIN` or `APP_PACKAGE`
- number of matching entries
- whether `onFillRequest` was received
- whether `onSaveRequest` was received

### Acceptance criteria

- [ ] Android provider is BSR Vault
- [ ] Chrome third-party mode is enabled where supported
- [ ] `onFillRequest` is received from Chrome
- [ ] Domain is detected correctly
- [ ] github.com matches GitHub entry
- [ ] accounts.github.com follows existing domain policy
- [ ] fake-github.com does not match GitHub
- [ ] github.com.evil.example does not match GitHub
- [ ] No package-based false match occurs in browser mode

---

## BUG-006 — Android Save-Password Popup Does Not Appear

**Status:** 🟥 BUG / NEEDS INVESTIGATION  
**Priority:** P0  
**Affected area:** `SaveInfo`, `onSaveRequest`, changed-field detection, Chrome/provider settings, form behavior.

### Known implementation concern

The current save flow reportedly creates a `SaveInfo.Builder` using password IDs and optional username IDs. This is a valid starting point, but Android only displays the save UI when the client app, form state, changed fields, provider selection, and lifecycle conditions permit it.

### Required investigation

Verify:

- `SaveInfo` is returned from `onFillRequest`
- correct username/password `AutofillId` values are used
- required fields are non-empty
- changed fields are detected
- `onSaveRequest` is actually invoked
- Chrome third-party mode is enabled
- the test form is a real supported login/signup form
- the form is submitted or finished in a way that triggers saving
- multi-step forms preserve state across FillContexts

### Multi-step forms

Evaluate use of:

```text
SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
```

Use it only where appropriate. Do not apply blindly to every form.

### New vs existing record logic

#### New credential

If no logical match exists for:

```text
domain + username
```

create a new entry.

#### Existing credential

If a matching entry exists:

- update the same entry
- preserve entry ID
- preserve createdAt
- preserve appPackage/appName
- preserve metadata
- update only changed fields
- create password history only when password actually changes

### Critical safety rule

If browser domain is missing or invalid, do not silently save an entry with:

```text
site = com.android.chrome
```

Instead:

- request more context if possible
- skip save safely
- record a non-sensitive diagnostic reason

### Acceptance criteria

- [ ] `SaveInfo` is returned for supported forms
- [ ] New signup triggers save UI where Android/client permits it
- [ ] New credential is created once
- [ ] Existing credential is updated
- [ ] Duplicate entries are not created
- [ ] Multiple usernames on one domain remain separate
- [ ] Password history works
- [ ] Multi-step form behavior is tested
- [ ] Missing domain does not create malformed browser records
- [ ] `onSaveRequest` diagnostics work without secrets

---

## BUG-007 — Auto-Lock Lifecycle May Reset on Activity Switch

**Status:** 🟨 PARTIAL / NEEDS VERIFICATION  
**Priority:** P1  
**Affected area:** lifecycle management, `MainActivity`, `SettingsActivity`, session state.

### Required architecture

Use application-level lifecycle handling, preferably:

```text
ProcessLifecycleOwner
```

### Required behavior

- On application `onStop`: record last active time.
- On application `onStart`: check whether session expired.
- If expired: lock vault.
- Switching between activities inside the app must not incorrectly reset the timer.
- No lock/unlock loop.
- Screen-off behavior must be tested separately.

### Acceptance criteria

- [ ] MainActivity → SettingsActivity does not reset timer
- [ ] SettingsActivity → MainActivity behaves correctly
- [ ] Background timeout locks vault
- [ ] Returning before timeout preserves session
- [ ] Returning after timeout requires unlock
- [ ] Biometric fallback still works
- [ ] Process lifecycle observer is registered only once

---

# 6. CURRENTLY COMPLETED OR PARTIALLY COMPLETED FEATURES

These statuses must be updated only after verification.

## 6.1 Existing vault and encryption

**Status:** 🟨 PARTIAL / PROTECTED

The existing architecture includes encrypted vault storage and existing authentication logic.

### Keep unchanged for v6.7

- AES-GCM
- PBKDF2/key derivation
- existing encrypted JSON format
- existing vault loading/saving
- existing password history
- existing backup compatibility

### Verification required

- [ ] Existing vault opens
- [ ] Old entries load
- [ ] Save does not corrupt JSON
- [ ] Password change works
- [ ] Backup restore works
- [ ] No data loss after edit

---

## 6.2 Existing login/unlock flow

**Status:** 🟨 PARTIAL / NEEDS VERIFICATION

Test:

- [ ] First-time vault setup
- [ ] Normal unlock
- [ ] Wrong password rejection
- [ ] Biometric unlock
- [ ] Autofill authentication
- [ ] Auto-lock and re-unlock
- [ ] Master password change

---

## 6.3 Existing Autofill field detection

**Status:** ⚫ DO NOT REWRITE IN v6.7**

Preserve existing:

- username detection
- password detection
- `AssistStructure` traversal
- autofill hints
- input type handling
- existing field classification

Only fix defects with evidence.

---

## 6.4 Existing browser/domain matching

**Status:** ⚫ DO NOT REPLACE IN v6.7**

Preserve:

- domain matching
- eTLD+1 behavior
- subdomain policy
- browser detection
- phishing-resistant negative cases

App package matching must not replace browser matching.

---

# 7. PREMIUM ROADMAP AFTER v6.7

## PHASE P1 — Premium Vault UX

**Status:** 🟪 FUTURE

Potential features:

- search
- categories
- folders
- tags
- favorites
- sorting
- recently used
- recently modified
- batch actions
- better empty states
- Material 3 modernization
- accessibility improvements

Do not rewrite the entire UI in one step.

---

## PHASE P2 — Password Generator

**Status:** 🟪 FUTURE

Features:

- random password generation
- passphrases
- PINs
- length controls
- uppercase/lowercase/numbers/symbols
- excluded characters
- secure random source
- copy with clipboard protection

---

## PHASE P3 — Clipboard Security

**Status:** 🟨 PARTIAL / FUTURE HARDENING

Potential features:

- `ClipDescription.EXTRA_IS_SENSITIVE`
- timed clipboard clearing
- clear only if clipboard still contains the BSR Vault value
- configurable timeout
- no destruction of newer user clipboard content

---

## PHASE P4 — TOTP Authenticator

**Status:** 🟪 FUTURE

Features:

- QR scanning
- manual secret entry
- issuer/account
- 6 or 8 digits
- SHA-1/SHA-256
- configurable period
- countdown timer
- encrypted storage
- copy protection

Do not modify core Autofill logic to add TOTP.

---

## PHASE P5 — Security Health

**Status:** 🟪 FUTURE

Checks:

- weak passwords
- reused passwords
- old passwords
- duplicate entries
- missing 2FA
- entries without app/domain linkage
- stale credentials

Do not log or expose password values.

---

## PHASE P6 — Backup and Import Expansion

**Status:** 🟪 FUTURE

Potential features:

- encrypted export
- backup validation
- corruption detection
- restore preview
- conflict handling
- Chrome/Edge CSV import
- Bitwarden JSON import
- KeePass import
- 1Password import

Do not replace the current BSR format without migration planning.

---

## PHASE P7 — Credential Manager / Passkeys

**Status:** 🟪 FUTURE — SEPARATE ARCHITECTURE

Potential features:

- Android Credential Manager
- passkeys
- WebAuthn
- FIDO2
- passwordless sign-in
- passkey metadata
- separate provider integration

Do not mix this into v6.7 password Autofill fixes.

---

# 8. FEATURES EXPLICITLY EXCLUDED FROM v6.7

## 8.1 PBKDF2 → Argon2id migration

**Status:** ⚫ DO NOT ADD

Requires:

- versioned encryption format
- migration path
- recovery testing
- rollback strategy
- old-vault compatibility
- backup compatibility

---

## 8.2 SQLCipher/Room migration

**Status:** ⚫ DO NOT ADD

Requires a separate database migration project.

---

## 8.3 ML-KEM/Kyber/post-quantum encryption

**Status:** ⚫ DO NOT ADD

Keep as future research only. Do not introduce experimental cryptography into the production vault without a formal threat model and migration design.

---

## 8.4 Duress/decoy vault

**Status:** ⚫ DO NOT ADD

Requires separate design for:

- metadata leakage
- backups
- recovery
- forensic behavior
- user safety
- authentication state

---

## 8.5 Self-destruct/wipe after failed attempts

**Status:** ⚫ DO NOT ADD

Risk of irreversible accidental data loss.

---

## 8.6 Accessibility floating bubble as Autofill replacement

**Status:** ⚫ DO NOT ADD

Native Android Autofill must be fixed first. Accessibility-based alternatives require separate privacy, permission, battery, and policy review.

---

## 8.7 Aggressive root/Frida/Xposed blocking

**Status:** ⚫ DO NOT ADD

Do not automatically block or destroy user data based on uncertain device-risk signals.

---

## 8.8 Full Compose/UI rewrite

**Status:** ⚫ DO NOT ADD IN v6.7

Use gradual screen-by-screen modernization later.

---

# 9. SECURITY RULES

Never log:

- master password
- entry password
- username when unnecessary
- TOTP secret
- recovery seed
- session key
- decrypted vault JSON
- encryption key
- raw Autofill values

Allowed diagnostics may include:

- package name
- domain
- field classification
- matching mode
- count of matches
- lifecycle event
- success/failure category
- timing

---

# 10. TEST MATRIX

## 10.1 Vault

- [ ] Create vault
- [ ] Unlock
- [ ] Lock
- [ ] Add entry
- [ ] Edit entry
- [ ] Delete entry
- [ ] Restore entry
- [ ] Search
- [ ] Multiple accounts
- [ ] Old vault compatibility

## 10.2 Entry types

- [ ] Login
- [ ] Card
- [ ] Identity
- [ ] Note
- [ ] Old entry without appPackage
- [ ] Entry with appPackage
- [ ] Null optional fields

## 10.3 App Picker

- [ ] Login only
- [ ] Installed app list
- [ ] Icon
- [ ] Name
- [ ] Package
- [ ] Search
- [ ] Select
- [ ] Clear
- [ ] Edit highlight
- [ ] Dark theme
- [ ] Missing label fallback

## 10.4 Autofill

- [ ] Native app
- [ ] Browser
- [ ] Chrome
- [ ] Provider selected
- [ ] Locked vault
- [ ] Unlocked vault
- [ ] Domain matching
- [ ] Subdomain matching
- [ ] Wrong-domain rejection
- [ ] Multiple accounts
- [ ] Save request
- [ ] Update request
- [ ] Multi-step form

## 10.5 Security

- [ ] Biometrics
- [ ] Auto-lock
- [ ] Screen off
- [ ] Activity switch
- [ ] Clipboard timeout
- [ ] Backup
- [ ] Restore
- [ ] Master password change
- [ ] No secrets in logs

---

# 11. AI AUTONOMOUS WORKFLOW

Any AI receiving this file must follow this process.

## Step A — Read status

- Read all `🟥 BUG`, `🟨 PARTIAL`, `⛔ BLOCKED`, and `❔ NEEDS VERIFICATION` items.
- Do not start future features while P0 bugs remain.

## Step B — Inspect repository

Identify:

- package name
- Gradle files
- manifest
- Activities
- Autofill Service
- `VaultManager`
- `PasswordEntry`
- `AddEditActivity`
- `SettingsActivity`
- `autofill_service_config.xml`
- layouts
- tests
- backup/restore code

## Step C — Produce a change plan

Before editing, list:

1. files to modify
2. files to leave untouched
3. exact cause of bug
4. minimal patch
5. regression tests
6. rollback plan

## Step D — Implement one issue

Do not combine unrelated large changes.

## Step E — Build

Run:

```text
clean build
debug build
release build where configured
```

## Step F — Test

Install APK and test the issue on a real device/emulator.

## Step G — Update this file

For each task, update:

- status marker
- date
- changed files
- test result
- remaining limitation
- next action

## Step H — Continue

Only move to the next priority after the current task has evidence.

---

# 12. REQUIRED STATUS UPDATE FORMAT

After each completed task, add an entry like this:

```text
## STATUS UPDATE — YYYY-MM-DD

### Task
BUG-001 — Edit password entry crash

### Previous status
🟥 BUG

### New status
🟩 DONE / 🟨 PARTIAL / ⛔ BLOCKED

### Root cause
Describe the verified cause.

### Files changed
- path/to/file1
- path/to/file2

### Fix summary
Describe only the actual changes.

### Tests performed
- Test 1
- Test 2
- Test 3

### Evidence
- Build result
- Logcat result
- Device result
- Screenshot/video if available

### Remaining issues
Describe anything still unresolved.

### Next recommended task
BUG-002 / BUG-003 / etc.
```

---

# 13. DEFINITION OF DONE FOR v6.7

v6.7 is not complete merely because the APK builds.

All of the following must pass:

- [ ] Clean build
- [ ] APK installs
- [ ] Settings opens correctly
- [ ] Settings lock gate works
- [ ] Edit crash fixed
- [ ] App Picker only appears for login
- [ ] App Picker save/edit/highlight works
- [ ] Old entries remain compatible
- [ ] Native app package matching works
- [ ] Browser domain matching remains correct
- [ ] Chrome provider setup tested
- [ ] Chrome domain Autofill tested
- [ ] SaveInfo tested
- [ ] New credential save tested
- [ ] Existing credential update tested
- [ ] Duplicate prevention tested
- [ ] Multi-step form tested
- [ ] Auto-lock tested
- [ ] Biometric tested
- [ ] Backup/restore tested
- [ ] No secrets in logs
- [ ] Changelog updated
- [ ] Regression checklist completed
- [ ] Known limitations documented

---

# 14. FINAL DECISION MATRIX

| Feature | v6.7 decision | Status |
|---|---|---|
| Existing VaultManager | Preserve | ⚫ PROTECTED |
| AES-GCM | Preserve | ⚫ PROTECTED |
| PBKDF2 | Preserve | ⚫ PROTECTED |
| Existing vault JSON | Preserve | ⚫ PROTECTED |
| Edit crash fix | Required now | 🟥 BUG |
| SettingsActivity exported | Required now | 🟥 BUG/TO VERIFY |
| Settings lock gate | Required now | 🟥 BUG/TO VERIFY |
| App Picker login-only | Required now | 🟥 BUG |
| App package save/edit | Required now | 🟨 PARTIAL |
| Browser domain matching | Preserve | ⚫ PROTECTED |
| SaveInfo | Fix/verify now | 🟥 BUG |
| Chrome integration | Test/fix now | 🟥 BUG |
| Auto-lock lifecycle | Fix/verify now | 🟨 PARTIAL |
| Clipboard security | Keep/improve carefully | 🟨 PARTIAL |
| Password generator | Future | 🟪 FUTURE |
| TOTP | Future | 🟪 FUTURE |
| Security Watchtower | Future | 🟪 FUTURE |
| Credential Manager | Future | 🟪 FUTURE |
| Passkeys/WebAuthn/FIDO2 | Future | 🟪 FUTURE |
| Argon2id migration | Not in v6.7 | ⚫ DO NOT ADD |
| SQLCipher/Room migration | Not in v6.7 | ⚫ DO NOT ADD |
| ML-KEM/Kyber | Not in v6.7 | ⚫ DO NOT ADD |
| Decoy vault | Not in v6.7 | ⚫ DO NOT ADD |
| Self-destruct wipe | Not in v6.7 | ⚫ DO NOT ADD |
| Accessibility bubble | Not in v6.7 | ⚫ DO NOT ADD |
| Full Compose rewrite | Not in v6.7 | ⚫ DO NOT ADD |

---

# 15. FINAL MASTER PRINCIPLE

BSR Vault का development इस क्रम में होना चाहिए:

```text
Existing Data Safety
        ↓
Authentication
        ↓
Vault Stability
        ↓
Edit/Add/Delete Reliability
        ↓
Android Autofill
        ↓
Save/Update Logic
        ↓
Chrome Compatibility
        ↓
Backup/Recovery
        ↓
Premium Features
```

**पहले existing user data और working features को सुरक्षित रखें। उसके बाद bugs fix करें। फिर premium features जोड़ें।**

यह roadmap AI/developer को स्वतंत्र निर्णय लेने की अनुमति देता है, लेकिन उसे:

- अनुमान से status बदलने,
- बिना test के बड़े बदलाव करने,
- protected crypto/data architecture बदलने,
- secrets log करने,
- और एक साथ कई unrelated systems rewrite करने

की अनुमति नहीं देता।

# END OF FINAL BSR VAULT ROADMAP
