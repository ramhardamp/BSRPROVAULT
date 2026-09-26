# BSR Vault v6.7 — Regression Test Checklist

## Implementation order gate
- [ ] Step 1: Edit Crash fix tested on device before Step 2
- [ ] Step 2: Settings exported + App Picker hide tested before Step 3
- [ ] Step 3: Save popup + ProcessLifecycleOwner auto-lock tested before Step 4
- [ ] Step 4: Chrome test completed last

## 1. Edit Crash — urgent gate
- [ ] Add/edit/save login
- [ ] Add/edit/save note
- [ ] Add/edit/save card
- [ ] Add/edit/save identity
- [ ] AddEdit without `id` still opens Add mode
- [ ] Blank/null edit ID extra → Toast + finish, no crash
- [ ] Non-existent edit ID → Toast + finish, no crash
- [ ] Missing layout view → Toast + finish, no `lateinit` crash

## 2. Settings / Autofill settingsActivity
- [ ] Android Autofill settings opens BSR SettingsActivity
- [ ] Locked Settings redirects to LoginActivity
- [ ] Successful unlock returns to Settings
- [ ] Version shows v6.7 dynamically

## 3. App Picker
- [ ] Login: picker visible
- [ ] Note/Card/Identity: picker hidden
- [ ] Existing linked login highlights selected app
- [ ] Save/reopen preserves `appPackage` + `appName`

## 4. Autofill matching
- [ ] Exact package match for normal app
- [ ] Legacy package-token fallback still works
- [ ] Browser requests never use appPackage matching
- [ ] Chrome + github.com matches GitHub domain entry
- [ ] eTLD+1/subdomain matching preserved
- [ ] `evil.co.in` ≠ `amazon.co.in`
- [ ] Multiple accounts for same domain are shown

## 5. Chrome 3rd-party Autofill
- [ ] Before test: Chrome → Settings → Autofill services → **Autofill using another service** ON
- [ ] Restart Chrome if prompted
- [ ] BSR Settings shows Chrome 3P status
- [ ] **Open Chrome Autofill Settings** opens Chrome settings
- [ ] If Chrome 3P is OFF, record this as a user-setting prerequisite, not a BSR chip bug
- [ ] Known login site shows BSR suggestion/chip

## 6. Chrome save popup
- [ ] New login → successful submit/commit → Android save UI appears when required values are non-empty and changed
- [ ] SaveInfo `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE` covers views becoming invisible
- [ ] Accept save → new BSR entry with correct domain/URL/user/password
- [ ] Existing same-domain + same-user → update same entry ID/password
- [ ] Missing browser domain never creates `site=com.android.chrome`

## 7. Auto-lock lifecycle
- [ ] 1-minute timeout
- [ ] Activity switching does not reset timer
- [ ] Background > timeout → lock on return
- [ ] Background < timeout → remains unlocked
- [ ] Unlock restarts session timing

## 8. Login / biometric / vault
- [ ] Setup → Main
- [ ] Master login → Main
- [ ] Biometric unlock
- [ ] Wrong-password lockout
- [ ] Master password change preserves vault
- [ ] Backup/restore preserves app link fields

## 9. Entry types
- [ ] Login add/edit/delete/restore
- [ ] Note add/edit/delete/restore
- [ ] Card add/edit/delete/restore
- [ ] Identity add/edit/delete/restore

## 10. Security / compatibility
- [ ] No password/OTP secrets in logs
- [ ] Old vault JSON without app link fields loads
- [ ] AES-GCM/PBKDF2 unchanged
- [ ] FieldClassifier tests pass
- [ ] PSL/eTLD+1 tests pass

## Final release gate
- [ ] Clean build from ZIP/checkout
- [ ] Debug APK installs
- [ ] All device tests pass
- [ ] No crash in Edit/Settings/Login/Autofill/Backup
- [ ] APK version is 6.7
