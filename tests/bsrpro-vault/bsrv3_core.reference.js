// ─── BSRPRO.Vault V3 core (shared spec: BSRPRO-VAULT-FORMAT-V3.md) ───────────
// file = Base64( header(38) || AES-256-GCM(ct+tag) )
// header = "BSRV" | 0x03 | 0x01(PBKDF2-SHA256) | iterations u32 BE (600000) | salt[16] | iv[12]   (whole header = GCM AAD)
// key = PBKDF2-HMAC-SHA256( UTF-8(master), "BSRPRO.Vault.v3.backup\0" || salt, 600000 )
// V3 is decrypted with V3 rules only — no fallback to weaker/legacy iteration counts.
const _BSRV3 = (() => {
  const ENC = new TextEncoder(), DEC = new TextDecoder();
  const PREFIX = 'QlNSVgMB', VERSION = 3, KDF_ID = 1, ITER = 600000;
  const SALT_LEN = 16, IV_LEN = 12, HEADER_LEN = 38;
  const DOMAIN = ENC.encode('BSRPRO.Vault.v3.backup\u0000');
  const fmtErr  = m => { const e = new Error(m); e.name = 'BsrFormatError'; return e; };
  const authErr = () => { const e = new Error('BSRPRO.Vault authentication failed'); e.name = 'BsrAuthError'; return e; };
  function toB64(bytes) {
    let bin = '';
    for (let i = 0; i < bytes.length; i += 8192) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
    return btoa(bin);
  }
  function fromB64(str) {
    let bin; try { bin = atob(str); } catch (e) { throw fmtErr('BSRPRO.Vault: base64 invalid'); }
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  async function deriveKey(password, salt, iterations, usage) {
    const kdfSalt = new Uint8Array(DOMAIN.length + salt.length);
    kdfSalt.set(DOMAIN, 0); kdfSalt.set(salt, DOMAIN.length);
    const km = await crypto.subtle.importKey('raw', ENC.encode(password), 'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey({ name: 'PBKDF2', salt: kdfSalt, iterations, hash: 'SHA-256' },
      km, { name: 'AES-GCM', length: 256 }, false, [usage]);
  }
  function isV3(text) {
    return typeof text === 'string' && text.replace(/^\uFEFF/, '').trimStart().startsWith(PREFIX);
  }
  async function encrypt(password, plaintext) {
    const salt = crypto.getRandomValues(new Uint8Array(SALT_LEN));
    const iv   = crypto.getRandomValues(new Uint8Array(IV_LEN));      // fresh random IV for every backup
    const header = new Uint8Array(HEADER_LEN);
    header.set([0x42, 0x53, 0x52, 0x56, VERSION, KDF_ID], 0);
    new DataView(header.buffer).setUint32(6, ITER, false);
    header.set(salt, 10); header.set(iv, 10 + SALT_LEN);
    const key = await deriveKey(password, salt, ITER, 'encrypt');
    const ct = new Uint8Array(await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: header, tagLength: 128 }, key, ENC.encode(plaintext)));
    const out = new Uint8Array(HEADER_LEN + ct.length);
    out.set(header, 0); out.set(ct, HEADER_LEN);
    return toB64(out);
  }
  async function decrypt(password, text) {
    const bytes = fromB64(String(text).replace(/^\uFEFF/, '').replace(/\s/g, ''));
    if (bytes.length < HEADER_LEN + 16) throw fmtErr('BSRPRO.Vault: file truncated');
    if (bytes[0] !== 0x42 || bytes[1] !== 0x53 || bytes[2] !== 0x52 || bytes[3] !== 0x56) throw fmtErr('BSRPRO.Vault: bad magic');
    if (bytes[4] !== VERSION) throw fmtErr('BSRPRO.Vault: unsupported version ' + bytes[4]);
    if (bytes[5] !== KDF_ID) throw fmtErr('BSRPRO.Vault: unsupported KDF');
    const iters = new DataView(bytes.buffer, bytes.byteOffset).getUint32(6, false);
    if (iters !== ITER) throw fmtErr('BSRPRO.Vault: iteration count not allowed for V3');   // no downgrade
    const header = bytes.slice(0, HEADER_LEN);
    const salt = bytes.slice(10, 10 + SALT_LEN), iv = bytes.slice(10 + SALT_LEN, HEADER_LEN);
    const ct = bytes.slice(HEADER_LEN);
    const key = await deriveKey(password, salt, iters, 'decrypt');
    let pt;
    try { pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv, additionalData: header, tagLength: 128 }, key, ct); }
    catch (e) { throw authErr(); }
    return DEC.decode(pt);
  }
  // Decrypt + strict all-or-nothing validation. Returns the inner payload string (entries JSON / CSV text).
  async function open(password, text) {
    const plain = await decrypt(password, text);
    let outer; try { outer = JSON.parse(plain); } catch (e) { throw fmtErr('BSRPRO.Vault: payload is not valid JSON'); }
    if (!outer || outer.format !== 'BSRPRO.Vault' || outer.version !== VERSION) throw fmtErr('BSRPRO.Vault: payload header invalid');
    if (typeof outer._bsrOrigin !== 'string' || !outer._bsrOrigin.startsWith('BABASITARAMPro:')) throw fmtErr('Origin verification failed');
    const inner = typeof outer.data === 'string' ? outer.data : JSON.stringify(outer.data);
    if (typeof outer.count === 'number') {
      let parsed; try { parsed = JSON.parse(inner); } catch (e) { throw fmtErr('BSRPRO.Vault: inner data invalid'); }
      const arr = Array.isArray(parsed) ? parsed : (parsed && Array.isArray(parsed.entries) ? parsed.entries : null);
      if (!arr) throw fmtErr('BSRPRO.Vault: entries missing');
      if (arr.length !== outer.count) throw fmtErr('BSRPRO.Vault: entry count mismatch — backup incomplete/corrupt');
      for (const e of arr) if (!e || typeof e !== 'object' || Array.isArray(e)) throw fmtErr('BSRPRO.Vault: malformed entry');
    }
    return inner;
  }
  async function seal(password, inner, origin) {
    let count = null;
    try { const p = JSON.parse(inner); count = Array.isArray(p) ? p.length : (p && Array.isArray(p.entries) ? p.entries.length : null); } catch (e) {}
    return encrypt(password, JSON.stringify({ _bsrOrigin: 'BABASITARAMPro:' + origin, format: 'BSRPRO.Vault', version: VERSION, ts: Date.now(), count, data: inner }));
  }
  return { isV3, encrypt, decrypt, open, seal, PREFIX, ITER };
})();
