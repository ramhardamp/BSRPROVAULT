# AUTOFILL-TEST.md — BSR Pro v6.5 (phone par test)

Har test ke baad **Settings → 🩺 Autofill Diagnostics → Copy** dabayein aur output dekhein/bhejein.
(Diagnostics mein koi password/username nahi hota. Screenshot FLAG_SECURE se blocked hai, isliye "Copy" use karein.)

## Pehle ek baar setup
1. BSR Pro kholein → main screen ka **DEFAULT BANAYEIN** banner (ya Settings → ⚡ Autofill ON karein) → system dialog mein Allow.
2. **Chrome:** Settings → **Autofill services** → **"Autofill using another service"** ON → Chrome band karke dobara kholein.
3. Ek test entry banayein: naam `GitHub`, **URL `https://github.com`**, apna username/password. (URL zaroori hai — browser match URL se hota hai.)

## TEST 1 — Gboard + Chrome (main case)
1. Chrome mein `github.com/login` kholein → **Username** field tap karein.
2. Expected (Android 11+ aur Gboard/Samsung keyboard): keyboard ke upar **chips** — `GitHub` aur `BSR Pro · Vault se chunein…`.
3. Chip tap → username + password dono bhar jayein.
4. Chips na aayein to **dropdown** mein "BSR Pro" dikhna chahiye.
5. Bonus (naya): `gist.github.com` / `accounts.google.com` jaise **subdomain** par bhi wahi entry aani chahiye (eTLD+1 match).

## TEST 2 — Native app (Instagram / Flipkart)
1. App ka login screen → username field tap.
2. Expected: chips/dropdown mein saved entry (package naam ke shabd se match: `instagram`, `flipkart`).
3. Match na mile → **"BSR Pro → Vault se chunein…"** chip → tap → poori vault list (search ke saath) → entry chunein → fill.

## TEST 3 — Naya login save
1. Nayi site/app par login karein → submit ke baad **"Save password?"** prompt.
2. Save → vault mein entry aa jaye (Diagnostics: `save: request handle hui`).
3. Agli baar wahi site → chips mein dikhe.

## TEST 4 — Locked vault
1. BSR Pro lock karein (ya auto-lock hone dein) → Chrome mein login field tap.
2. Expected: **"🔐 BSR Pro — Unlock"** chip → tap → fingerprint/master password.
3. Unlock ke baad matching entries fill hone ke liye aa jayein (Diagnostics: `unlock ke baad: response bheja=true`).

## TEST 5 — Diagnostics kaise padhein
Sahi chalne par log kuch aisa dikhta hai (naya upar):
```
response: matches=1 bheja=true
fill: app=com.android.chrome domain=github.com userFields=1 passFields=1 locked=false | inline request AAYI: max=3 specs=3 -> chips<=3
fields: U(id=- it=0x0 html:text) ; P(id=- it=0x0 html:password)
```
| Diagnostics mein dikhe | Matlab | Kya karein |
|---|---|---|
| `Enabled: false` ya koi `fill:` line hi nahi | BSR Pro default autofill nahi / Chrome mein option OFF | Setup (upar) dobara, Chrome restart |
| `fill: koi login field nahi mila` | Field pehchana nahi gaya | `fields:` line copy karke bhejein — classifier isi se theek hoga |
| `userFields=0 passFields=1` + `username=fallback` | Username field ka naam pehchana nahi, password se pehle wala text field le liya | Normal; galat field bhare to bhejein |
| `matches=0` | Is site/app ke liye entry nahi mili | Entry mein **URL** bharein (https://site.com); tab tak "Vault se chunein" chip use karein |
| `inline request nahi aayi` | Keyboard/Android inline support nahi karta (Android <11 ya purana keyboard) | Gboard / Samsung Keyboard; warna dropdown aayega — yeh normal hai |
| `inline: keyboard ka style v1 support nahi karta` | Keyboard purana UI-version | Keyboard update karein; dropdown aayega |
| `ERROR fill: ...` | Code mein exception | Poori line bhejein |
| `PSL loaded: false` | Domain suffix list load nahi hui | Bhejein (fallback list se sirf common .co.in/.co.uk chalenge) |
