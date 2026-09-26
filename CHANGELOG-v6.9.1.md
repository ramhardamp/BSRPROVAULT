# v6.9.1 — Diagnostics & Self-Test merge + root-cause fix

## Asli root cause mila
`SelfTestActivity.kt` mein `androidx.lifecycle.lifecycleScope` use hota tha, par `build.gradle` mein
sirf `androidx.lifecycle:lifecycle-process` thi — jo alag artifact hai aur `lifecycleScope` extension
nahi deti (wo `androidx.lifecycle:lifecycle-runtime-ktx` mein hai). Isi wajah se yeh screen kaam nahi
kar rahi thi. Ab yeh dependency `app/build.gradle` mein jodi gayi hai.

## Kya badla
- **NEW `DiagnosticsActivity.kt`** — pehle ki teen screens (`SystemDiagnosticsActivity`,
  `AutofillDiagnosticsActivity`, `SelfTestActivity`) ab ek hi screen mein:
  - Device / App install / Vault-Security / Autofill+Chrome+PSL+Keyboard / Backup / UI-Theme —
    system report ka har hissa.
  - Safe in-memory self-test: AES-GCM round-trip, `.bsrpro` round-trip, BSRPRO.Vault V3 round-trip +
    wrong-password reject, export guard, CSV parser, dedup key, password strength — koi vault write
    nahi hota.
  - Recent System + Autofill event log (dono).
  - Ek hi top-line verdict: ✅ PASS / ⚠️ WARN / ❌ FAIL, neeche poora detail.
- **Robustness**: har check apne try/catch mein (pehle jaisa hi), plus poora report-builder ab ek
  bahar wale try/catch mein bhi hai — koi anjaana crash bhi ab kabhi khaali/crashed screen nahi
  dega, kam se kam partial report + us exception ka FAIL line milega.
- `AndroidManifest.xml`: teen activities → ek `DiagnosticsActivity`.
- `SettingsActivity.kt` / `activity_settings.xml`: teen buttons ("Autofill Diagnostics", "System
  Diagnostics", "Full Self-Test") → ek button "🩺 Diagnostics & Self-Test".
- Purani teen files (`SystemDiagnosticsActivity.kt`, `AutofillDiagnosticsActivity.kt`,
  `SelfTestActivity.kt` + unki layouts) hata di gayi.

## Verified
- `tools/verify_android_refs.py` PASS (146 ids, koi duplicate/dangling reference nahi).
- Sabhi XML well-formed.
- Naye/badle Kotlin ko is baar **stub Android API ke saath poora error-free compile karke** dikhaya
  (pehle sirf partial stub ki wajah se kuch unresolved-reference errors reh jaate the — is baar wo
  sab bhi theek karke 0 error hasil kiya).
- `root.get(...).asString` (Gson JsonObject) wali line asli Gson library banakar alag se verify ki —
  compile aur run dono sahi.
- Extension V3-export-gate CI test aur OTP/2FA test-suite dobara chalaya — sab pass (is fix ka
  extension se koi lena-dena nahi tha, sirf confirm kiya ki kuch toota nahi).
