# v6.7.9 — .vaultbak fix + .bsrpro removed
- FIX: `.vaultbak` export ab "wt" (truncate) mode mein likhta hai + size verify. Pehle Android 10+ par same naam se
  dobara export karne par purani badi file ki poonchh bachi rehti thi -> file corrupt -> import nahi hoti thi.
- CSV export mein bhi wahi truncate fix.
- Auto-backup ab `BabaSitaRam Pro Password ManagerPRO.vaultbak` (extension wali file, extension + APK dono mein import) banata hai.
- Purani `BabaSitaRamPro-AutoBackup.bsrpro` naya .vaultbak likhne ke baad apne aap delete ho jaati hai.
- `.bsrpro` export button hata diya. (Purani .bsrpro files import abhi bhi padh sakte hain.)
- FIX (logic): import/restore mein agar backup ki entry ka `id` vault mein pehle se ho to ab naya id milta hai
  (pehle same id ki 2 entries ban jaati thi -> edit/delete galat entry par lagta tha). `VaultManager.mergeFresh()`.
- FIX (logic): backup agar purane/alag Master Password se bani ho to import ab us backup ka password puchh kar retry karta hai.
- FIX (logic): auto-backup writes ab lock ke andar (do saath likhne se file corrupt nahi hogi); ".bin" naam jodne wale providers par file rename.
- FIX (logic): VaultManager ke saare read/write functions `@Synchronized` — autofill service aur UI/IO threads ek saath vault list badalte the (ConcurrentModificationException / adhoora save ka risk).
- FIX (logic): Vault reset (Settings + "Forgot") ab auto-backup folder pointer bhi hata deta hai; pehle naye khali vault ka pehla save purani achchi .vaultbak ko overwrite kar deta tha.
- FIX (logic): AddEdit aur Backup screen par auto-lock ke baad wapas aane par ab Login screen aati hai (pehle vault locked hone par save silently fail hota tha).
- FIX (Xiaomi/Redmi/POCO): biometric ab `BIOMETRIC_WEAK` maangta hai (pehle STRONG). MIUI/HyperOS par fingerprint/face WEAK class ke hote hain,
  isliye vault-unlock prompt mein sirf Pattern/PIN chalta tha. Naya `BioAuth.kt`; API 28-29 par negative button "Master Password use karein".
- Build-check: kotlinc se sabhi .kt files ka syntax parse (0 syntax error), resource/manifest reference audit, gradle wrapper + workflow paths verify.
- FIX: Master Password change ab background thread par (PBKDF2 main thread par hone se ANR/freeze ka risk tha).
- FIX: Generator — slider label 16 dikhata tha par password 24 ka banta tha; ab 16. Har chuni category se kam se kam 1 character + SecureRandom shuffle (pehle random password kisi category ke bina ban sakta tha).
- FIX: Generator ka Copy ab `SecureClip` se: sensitive flag + timer par auto-clear (pehle bina clear ke clipboard mein rehta tha).
- FIX: Autofill BROWSERS list mein Xiaomi/MIUI/HyperOS aur anya browsers jode — ye ab native app ki tarah token-fallback se match nahi hote.
