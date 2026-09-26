# Package A (Native Kotlin) vs Package B (Capacitor / APK/android-source in extension zip)

| | A: Native (yeh project) | B: Capacitor wrapper (extension zip mein) |
|---|---|---|
| Chrome / dusre apps mein password fill (Autofill Service) | HAAN — keyboard chips + dropdown + save prompt | NAHI — koi AutofillService hai hi nahi |
| Kabhi build hua? | Compile-verified (real Android 34 API + real Gson), CI ready | Nahi — apne docs ke hisaab se "template", npm/cap add zaroori |
| Build tarika | GitHub Actions / Codemagic / Android Studio (Gradle) | Node + `npx cap add android` + native files merge + Gradle |
| Extension ke features | v6.3: Cards, Identity, Folders, Tags, History, TOTP, Audit, CSV/KeePass/NordPass | Poori extension UI (WebView), lekin sirf app ke andar |
| Extension .vaultbak compatibility | Import + Export dono (test kiya) | Same code (same format) |

**Faisla:** A aage badhaya gaya. B akela "mobile par password bharne" ka kaam nahi kar sakta.
