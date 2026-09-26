# Extension ↔ Android compatibility report (v6.4)

Test method: extension ka ASLI JavaScript code (v5.40.1, `src/import-export.js`, `src/crypto.js`) Node + jsdom mein chalaya;
Android ka ASLI Kotlin code (BackupManager / VaultManager, real Gson) JVM par chalaya. Dono taraf ki files ek-doosre mein daali gayin
aur har field compare hui. (Scripts: `tests/compat/`)

## Extension → Android (extension ki asli export files → Android import)
| Extension file | Result |
|---|---|
| Manual export `.vaultbak` | ✅ sab fields: id, title, url, username, mobile, password, notes, tags, custom fields, TOTP, card, identity, idExpiry, folders, history, starred, category, createdAt |
| Auto-backup `.vaultbak` (`vault_backup` wrapper, VaultCrypto 600k) | ✅ same (folder/activity-log meta records skip, folders sahi map) |
| Encrypted `.json` / plain `.json` | ✅ sab fields (folders nahi — extension in exports mein folder list likhta hi nahi) |
| Encrypted `.csv`, Vault CSV, Chrome, Bitwarden, LastPass CSV | ✅ jo columns format mein hain woh sab |
| KeePass XML | ✅ |

## Android → Extension (Android ki files → extension ka ASLI importer)
| Android file | Result |
|---|---|
| "Extension ke liye export" `.vaultbak` | ✅ 8/8 entries, har field extension ki whitelist tak identical; password change + history; Android-only category (Shopping) → tag; folders map |
| Same, "Bina folders (safe)" | ✅ extension ke maujooda folders SALAMAT (bare-array payload) |
| Vault CSV (extension ka column order) | ✅ |
| Chrome CSV | ✅ |
| 2000 entries | ✅ (extension mein 0.5 s, Android import 0.8 s / export 1.5 s JVM par) |
| Extension mein pehle se wahi entries | ✅ 7 duplicate pehchane, sirf 1 nayi add |

## Extension ke apne niyam jo Android nahi badal sakta
* Extension `.vaultbak` import mein file ke `folders` us ke apne folders ko REPLACE kar deti hai (aur `folders` na ho to bhi `[]` maan leta hai).
  Isliye Android "Bina folders (safe)" mein entries ko bare array mein bhejta hai.
* Extension import sirf ek whitelist ke fields rakhta hai — `idExpiry`, `lastUsedAt` jaise fields extension mein wapas aate hi drop ho jaate hain.
* Android ki apni `.bsrpro` extension nahi padh sakti — extension ke liye hamesha "Extension ke liye export (.vaultbak)" use karein.
* Extension ki plain `.json` / `.csv` exports mein folder/tags/cards nahi hote; poori jaankari ke liye `.vaultbak` use karein.
