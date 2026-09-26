# BSR Vault (BabaSitaRam Pro) — Android (v6.5, UI refresh)

Password manager + **Android Autofill Service** (Chrome, Firefox aur baaki apps mein login fill).

## Features
- **Extension v5.40.1 ke saath poora compatible**: Cards, Identity, Folders, Tags, Custom fields, Password history
  (extension ka .vaultbak import hota hai, aur Android se extension ke liye .vaultbak export bhi)
- **Import**: BSR .bsrpro, Extension .vaultbak/.json, CSV (Chrome, Bitwarden, LastPass, 1Password, NordPass), KeePass XML
- **Tez vault**: har save par dobara key nahi banti (PBKDF2 sirf unlock par) — save/edit/import bahut tez
- **Data safety**: vault file kharab ho to khali vault se overwrite nahi hota; har save par pichla copy (`pw_prev`) bacha rehta hai
- **🩺 Autofill Diagnostics** (Settings): phone, keyboard, autofill status aur last requests ka log — kuch na chale to "Copy" dabakar bhej dein
- **Keyboard ke upar suggestions** (Android 11+, Gboard / Samsung Keyboard jaise keyboards): login field par tap karte hi
  username/password ke chips keyboard ki suggestion bar mein aate hain. Purane Android / keyboards par dropdown aata hai.
- **Vault se chunein** chip: app ka match na mile to bhi koi bhi login search karke chun sakte hain
- **Locked vault chip**: "🔐 BSR Pro — Unlock" — fingerprint/password ke baad fill
- Main screen par **Autofill setup banner** (ek tap mein default banayein)
- Master Password + AES-256-GCM vault, Fingerprint unlock (Android Keystore), auto-lock
- **Autofill** (default service set karne ka button) + naya login **save prompt**
- **2FA / TOTP** codes (base32 key ya otpauth:// link)
- **Secure Notes**, categories, favorites, search
- **Trash** (30 din tak restore), **Password history** (purane 10 passwords)
- **Security Audit**: weak / repeat / purane passwords + **Leak check** (HaveIBeenPwned, sirf hash ke 5 chars jaate hain)
- **Screenshot / recent-apps block**, galat password par **lockout** (5 galat = 30s, phir badhta jaata hai)
- **Auto-backup**: chune hue folder mein har badlav ke baad encrypted backup
- **Import**: BSR .bsrpro, Extension .vaultbak/.json, CSV (Chrome, Bitwarden, LastPass, 1Password)
- **Export**: encrypted .bsrpro aur CSV
- Clipboard auto-clear (Android 13+ par "sensitive" mark)


## Autofill setup (ek baar)
1. App → main screen ka **DEFAULT BANAYEIN** banner (ya Settings → ⚡ Autofill ON karein) → Allow.
2. **Chrome:** Settings → Autofill services → **"Autofill using another service"** ON → Chrome restart.
3. Entry mein **URL** zaroor bharein (browser match domain se hota hai; subdomain bhi chalta hai).
4. Login field tap → keyboard ke upar chips (Android 11+) ya dropdown → tap → fill. Poori test-list: `AUTOFILL-TEST.md`.

## 🩺 Autofill Diagnostics
Settings → **🩺 Autofill Diagnostics**: phone/keyboard/autofill status + recent requests ka log.
**Copy** dabakar bhejein — isme koi password/username nahi hota. Kuch na chale to sabse pehle yahi dekhein.

## APK banane ke 3 tareeke
### 1) GitHub Actions (sabse aasaan)
1. GitHub par repository banayein, is folder ki files upload karein (`.github/workflows/build.yml` zaroor).
2. **Actions → Build APK** (5–8 min) → Artifacts se `app-debug.apk` download.

### 2) Codemagic (agar GitHub na chale)
codemagic.io par repository jodein; `codemagic.yaml` maujood hai → Start build → APK download.

### 3) Android Studio (laptop par)
Folder kholein → Build → Build APK(s). (JDK 17)

## Autofill ON karna
1. App → Settings → **⚡ Autofill ON karein** → system dialog mein Allow.
2. **Chrome:** Settings → Autofill services → "Autofill using another service" ON.
3. Test: github.com/login par field tap → suggestion.

## Extension se data laana
Extension ka auto-backup (`...PasswordManagerPRO.vaultbak`) ya manual export → App → Settings → Backup & Restore → Import → extension wala Master Password.

## Zaroori
- Entry mein **URL zaroor bharein** (browser match URL/domain se hota hai).
- Kuch banking apps autofill block karte hain — wahan koi bhi autofill service nahi chalti.
- CSV export **unencrypted** hoti hai — kaam ke baad delete karein.
