const { JSDOM } = require('jsdom');
const fs = require('fs');
const { webcrypto } = require('crypto');
const EXT = '/home/claude/n/BabaSitaRam_PRO_MERGED_FINAL/CHROME';

function boot() {
  const html = fs.readFileSync(EXT + '/passwords.html', 'utf8').replace(/<script[\s\S]*?<\/script>/g, '');
  const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://localhost/' });
  const w = dom.window;
  const ctx = dom.getInternalVMContext();
  const vm = require('vm');
  Object.defineProperty(w, 'crypto', { value: webcrypto, configurable: true });
  w.Uint8Array = Uint8Array; w.ArrayBuffer = ArrayBuffer; w.TextEncoder = TextEncoder; w.TextDecoder = TextDecoder;
  w.console = console;
  const srcs = ['src/crypto.js', 'src/import-export.js'];
  for (const s of srcs) {
    let code = fs.readFileSync(EXT + '/' + s, 'utf8');
    try { new vm.Script(code, { filename: s }).runInContext(ctx); } catch (e) { console.log('EVAL ERR in', s, e.message); }
  }
  w.__ctx = ctx; w.__vm = vm;
  return w;
}
module.exports = { boot };
if (require.main === module) {
  const w = boot();
  console.log('functions:', ['encryptBackup','decryptBackup','parseFile','deduplicateImport','exportVaultCSV','exportKeePassXML','ImportExportUI','VaultCrypto'].map(n => n + '=' + typeof w[n]).join(' '));
  (async () => {
    const enc = await w.encryptBackup('{"hello":"wörld"}', 'pässw0rd');
    const dec = await w.decryptBackup(enc, 'pässw0rd');
    console.log('roundtrip:', dec);
  })().catch(e => console.log('ERR', e));
}
