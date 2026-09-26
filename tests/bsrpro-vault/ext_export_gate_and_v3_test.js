const fs = require('fs'), vm = require('vm');
const BROWSER = process.argv[2] || 'CHROME';
const R = require('path').resolve(__dirname, '../../../Extensions', BROWSER) + '/';
let fails = 0; const check = (n, ok) => { console.log((ok ? 'PASS  ' : 'FAIL  ') + n); if (!ok) fails++; };
const store = {};
const ns = { storage: { local: { get: (k, cb) => { const o = {}; (Array.isArray(k) ? k : [k]).forEach(x => { if (x in store) o[x] = store[x]; }); cb ? cb(o) : 0; return Promise.resolve(o); } } }, runtime: { id: 'testext' } };
const downloads = [];
const ctx = { console, crypto: globalThis.crypto, TextEncoder, TextDecoder, atob, btoa, setTimeout, clearTimeout, DOMException, Date, JSON, Math, Uint8Array, Promise,
  chrome: ns, browser: ns, location: { origin: 'chrome-extension://x' },
  Blob: class { constructor(p, o) { this.p = p; this.o = o; } }, URL: { createObjectURL: b => { downloads.push(b.p[0]); return 'blob:x'; }, revokeObjectURL() {} },
  document: { getElementById: () => null, createElement: () => ({ click() {}, set href(v) {}, set download(v) { downloads.name = v; } }), body: { appendChild() {}, removeChild() {} }, addEventListener() {} },
  navigator: {}, DOMParser: class {}, FileReader: class {} };
ctx.window = ctx; ctx.self = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(R + 'src/crypto.js', 'utf8'), ctx);
let src = fs.readFileSync(R + 'src/import-export.js', 'utf8');
vm.runInContext(src + '\n;this.__x={_ExportGate,_BSRV3,downloadFile,encryptBackup,decryptBackup,exportVaultCSV,exportChromeCSV,exportBitwardenCSV,exportLastPassCSV,exportKeePassXML,exportVaultJSON,ImportExportUI};', ctx);
const X = ctx.__x; const VC = vm.runInContext('VaultCrypto', ctx);
const ents = [{ id: 'a', title: 'GitHub', url: 'https://github.com', username: 'u', password: 'pä$$', notes: '', folderId: 'f1' }, { id: 'b', title: 'Bank', url: 'https://b.com', username: 'v', password: 'q' }];
(async () => {
  const throwsSync = (f) => { try { f(); return false; } catch (e) { return /verification required/.test(e.message); } };
  const throwsAsync = async (f) => { try { await f(); return false; } catch (e) { return /verification required/.test(e.message); } };
  // 1. No grant => nothing can be built or written
  check('downloadFile blocked without grant', throwsSync(() => X.downloadFile('x', 'a.csv', 'text/csv')));
  for (const fn of ['exportVaultCSV', 'exportChromeCSV', 'exportBitwardenCSV', 'exportLastPassCSV', 'exportKeePassXML', 'exportVaultJSON'])
    check(fn + ' blocked without grant', throwsSync(() => X[fn](ents)));
  check('encryptBackup blocked without grant', await throwsAsync(() => X.encryptBackup('[]', 'pw')));
  // 2. verification is fail-CLOSED
  check('deny when no verifier stored', (await X._ExportGate.verifyAndGrant('pw')) === false);
  store.vault_hash = await VC.hashMaster('Master#123');
  check('wrong master denied', (await X._ExportGate.verifyAndGrant('wrong')) === false);
  check('still blocked after denial', throwsSync(() => X.exportVaultCSV(ents)));
  const savedVerify = VC.verifyMaster; VC.verifyMaster = async () => { throw new Error('boom'); };
  check('verifier exception => denied (fail-closed)', (await X._ExportGate.verifyAndGrant('Master#123')) === false);
  VC.verifyMaster = savedVerify;
  check('correct master granted', (await X._ExportGate.verifyAndGrant('Master#123')) === true);
  // 3. with grant: build BSRPRO.Vault V3 + roundtrip
  const payload = JSON.stringify({ version: '2.0', app: 'BABASITARAMPro', count: ents.length, entries: ents, folders: [{ id: 'f1', name: 'Work' }] });
  const file = await X.encryptBackup(payload, 'Master#123');
  check('export is V3 (starts with QlNSVgMB)', file.startsWith('QlNSVgMB'));
  const inner = await X.decryptBackup(file, 'Master#123');
  check('roundtrip entries', JSON.parse(inner).entries.length === 2 && JSON.parse(inner).folders[0].name === 'Work');
  X.downloadFile(file, 'BSRPRO.Vault', 'application/octet-stream'); check('write allowed with grant', downloads.length === 1);
  X._ExportGate.revoke();
  check('blocked again after revoke', throwsSync(() => X.downloadFile('x', 'a', 'b')));
  // 4. wrong pw / tamper / truncated => reject whole file
  const bad = async (t, pw) => { try { await X.decryptBackup(t, pw); return false; } catch (e) { return ['BsrAuthError', 'BsrFormatError'].includes(e.name); } };
  check('wrong password rejected', await bad(file, 'nope'));
  const b = Buffer.from(file, 'base64'); b[b.length - 3] ^= 1;
  check('tampered rejected', await bad(b.toString('base64'), 'Master#123'));
  check('truncated rejected', await bad(file.slice(0, 200), 'Master#123'));
  // 5. interop files produced by the Kotlin/Android side
  for (const f of [process.env.ANDROID_V3_FIXTURE || '/nonexistent']) if (fs.existsSync(f)) {
    const t = fs.readFileSync(f, 'utf8'); const i = await X.decryptBackup(t, 'Master#123'); const p = JSON.parse(i);
    const arr = Array.isArray(p) ? p : p.entries;
    check('Android BSRPRO.Vault opens in extension (' + arr.length + ' entries)', arr.length === 3 && arr.some(e => e.password === 'p1-ünï'));
  }
  // 6. legacy compat: legacy .vaultbak produced by the ORIGINAL (unpatched) extension code still decrypts
  if (fs.existsSync(process.env.LEGACY_FIXTURE || '/nonexistent')) {
    const t = fs.readFileSync(process.env.LEGACY_FIXTURE || '/nonexistent', 'utf8'); const i = await X.decryptBackup(t, 'Master#123');
    check('legacy .vaultbak (310k) still imports', JSON.parse(i).entries.length === 2);
  }
  console.log(fails ? 'FAILURES: ' + fails : 'ALL ' + BROWSER + ' EXTENSION CHECKS PASSED');
  process.exit(fails ? 1 : 0);
})().catch(e => { console.error('HARNESS ERROR', e); process.exit(2); });
