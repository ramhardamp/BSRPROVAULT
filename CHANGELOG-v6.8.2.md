# v6.8.2 — Full deep audit (Android project)

Real code fix:
- **AddEditActivity.kt**: screen rotate/config-change hone par (naya login banate waqt) selected
  app-link (App Picker se chuna hua app) gayab ho jaata tha, kyunki `appPackage`/`appName` kisi View
  se bandhe nahi the (EditText/Spinner apna state khud save kar lete hain, ye do variables nahi).
  Ab `onSaveInstanceState`/restore se bachaye jaate hain.

Hygiene:
- `CHECKSUMS.sha256` stale tha (2 files match nahi kar rahe the) — regenerate kiya.

Verified safe (no change needed) — full trace, koi bug nahi mila:
- Login → Unlock → Settings → wapas Unlock flow: koi infinite redirect / duplicate activity nahi.
- Add → Edit → Save → Update → Delete → Restore → Empty-trash → Reset: har jagah snapshot +
  rollback-on-save-failure hai.
- Autofill: browser-vs-app package matching precedence sahi; save flow existing entry ko update
  karta hai (duplicate nahi banata); locked vault sahi handle hota hai.
- Data model backward-compat: `PasswordEntry` ke har field mein default value hai, isliye Kotlin
  synthetic no-arg constructor bantа hai aur Gson use karta hai — purana vault JSON (naye fields ke
  bina) load karne par koi field null nahi banta, koi crash nahi. Yeh Gson 2.10.1 (jo project use
  karta hai) se khud test karke confirm kiya gaya hai.
- Export/Import: `ExportGate.require()` builder-layer par lagा hai (sirf UI mein nahi), isliye bypass
  nahi ho sakta.
- Logs: password/OTP kabhi nahi likhe jaate; `SystemLog` mein redaction filter bhi hai.
- Resources: koi duplicate/missing `@+id`, `@color`, `@drawable`, `@style`, `@layout` reference nahi.
- Release build: signing config aur minify config sahi hain; koi debug-only leak nahi.
