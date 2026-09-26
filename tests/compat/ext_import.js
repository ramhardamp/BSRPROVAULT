const fs = require('fs');
const { boot } = require('./load');
const { entries, folders, MASTER } = require('./dataset');
const D = '/home/claude/xfer/';
let fails = 0;
const chk = (fmt, what, exp, got) => { const a = JSON.stringify(exp), b = JSON.stringify(got); if (a !== b) { fails++; console.log(`  MISMATCH [${fmt}] ${what}: expected=${a} got=${b}`); } };

async function importFile(name, fmt, existing = [], existingFolders = [], format = 'vault-csv') {
  const w = boot(); const R = (c) => w.__vm.runInContext(c, w.__ctx);
  R('globalThis.__entries = ' + JSON.stringify(existing) + '; globalThis.__folders = ' + JSON.stringify(existingFolders) + '; globalThis.__toast=""; globalThis.__foldersSet = null; globalThis.__saved = null;');
  const ui = R(`new ImportExportUI(() => globalThis.__entries, async (e) => { globalThis.__saved = e; globalThis.__entries = e; }, (p) => 70, () => 'gen' + Math.random().toString(36).slice(2,8), (m, err) => { globalThis.__toast = (err ? 'ERR ' : '') + m; }, () => {}, () => globalThis.__folders, async (f) => { globalThis.__foldersSet = f; })`);
  ui._confirmMasterPassword = async () => MASTER; ui._askImportPw = async () => MASTER;
  ui.selectedFormat = format;
  const text = fs.readFileSync(D + name, 'utf8');
  const file = new w.File([text], name);
  await ui._loadFile(file);
  const toast1 = R('globalThis.__toast');
  await ui._doImport();
  const saved = JSON.parse(R('JSON.stringify(globalThis.__saved)') || 'null');
  const fs2 = JSON.parse(R('JSON.stringify(globalThis.__foldersSet)') || 'null');
  return { saved, folders: fs2, toast: toast1 };
}
(async () => {
  const src = JSON.parse(fs.readFileSync(D + 'source.json', 'utf8'));
  const edited = JSON.parse(fs.readFileSync(D + 'android_edited.json', 'utf8'));   // Android-side model
  const WL = ['title','url','username','mobile','password','notes','tags','customFields','isNote','recordType','cardNumber','cardholder','cardExpiry','cardCvv','fullName','email','phone','address','idNumber','totp','passwordHistory','starred'];

  // ===== 1) Android .vaultbak -> REAL extension importer =====
  console.log('== android_ext.vaultbak (with folders) -> extension');
  let r = await importFile('android_ext.vaultbak');
  console.log('  toast:', r.toast);
  if (!r.saved) { fails++; console.log('  NO ENTRIES SAVED'); }
  else {
    chk('vaultbak', 'count', 8, r.saved.length);
    const fmap = {}; (r.folders || []).forEach(f => fmap[f.id] = f.name);
    for (const s of src.entries) {
      const g = r.saved.find(e => e.title === s.title);
      if (!g) { fails++; console.log('  MISSING', s.title); continue; }
      const expected = { ...s };
      if (s.title === 'SBI NetBanking') { expected.password = 'New#Sbi_2027'; expected.passwordHistory = [{ pw: 'S#bi_2026', changedAt: 1800000000000 }]; }
      for (const k of WL) chk('vaultbak', s.title + '.' + k, expected[k], g[k]);
      chk('vaultbak', s.title + '.folder', src.folders.find(f => f.id === s.folderId)?.name || '', fmap[g.folderId] || '');
      chk('vaultbak', s.title + '.category', s.category, g.category);
    }
    const amz = r.saved.find(e => e.title === 'Amazon');
    chk('vaultbak', 'Amazon (Android-only category) present+tag', true, !!amz && Array.isArray(amz.tags) && amz.tags.includes('prime'));
    console.log('  Amazon in extension: category=' + amz?.category + ' tags=' + JSON.stringify(amz?.tags) + ' folder=' + (fmap[amz?.folderId] || ''));
    chk('vaultbak', 'folders replaced with file folders', true, Array.isArray(r.folders) && r.folders.some(f => f.name === 'Office'));
    chk('vaultbak', 'idExpiry (extension whitelist drops it)', undefined, r.saved.find(e => e.title === 'Aadhaar').idExpiry);
  }

  // ===== 2) no folders in Android data => extension folders must NOT be wiped =====
  console.log('== android_ext_nofolders.vaultbak -> extension (existing folders must survive)');
  r = await importFile('android_ext_nofolders.vaultbak', 'x', [], [{ id: 'keep1', name: 'My Existing Folder' }]);
  chk('nofolders', 'setFolders not called (null)', null, r.folders);
  chk('nofolders', 'entries imported', 8, r.saved ? r.saved.length : 0);

  // ===== 3) Android vault CSV -> extension default "vault-csv" (position based) =====
  console.log('== android_vault.csv -> extension (vault-csv format)');
  r = await importFile('android_vault.csv', 'x', [], [], 'vault-csv');
  console.log('  toast:', r.toast);
  if (!r.saved) { fails++; console.log('  NO ENTRIES SAVED'); }
  else for (const s of edited) {
    if (s.type !== 'login' && !s.site) continue;
    const g = r.saved.find(e => e.title === s.site);
    if (!g) { if (s.type === 'login') { fails++; console.log('  MISSING', s.site); } continue; }
    chk('vault-csv', s.site + '.username', s.username, g.username);
    chk('vault-csv', s.site + '.mobile', s.mobile, g.mobile);
    chk('vault-csv', s.site + '.password', s.password, g.password);
    chk('vault-csv', s.site + '.notes', s.notes, g.notes);
  }

  // ===== 4) Android "Chrome" CSV -> extension chrome format =====
  console.log('== android_chrome.csv -> extension (chrome format)');
  r = await importFile('android_chrome.csv', 'x', [], [], 'chrome');
  console.log('  toast:', r.toast);
  if (!r.saved) { fails++; console.log('  NO ENTRIES SAVED'); }
  else for (const s of edited.filter(e => e.type === 'login' && e.password)) {
    const g = r.saved.find(e => e.title === s.site);
    if (!g) { fails++; console.log('  MISSING', s.site); continue; }
    chk('chrome', s.site + '.password', s.password, g.password);
    chk('chrome', s.site + '.username', s.username || s.mobile, g.username);
  }
  console.log(fails === 0 ? 'ALL ANDROID->EXT CHECKS PASSED' : 'MISMATCHES: ' + fails);
})().catch(e => { console.log('ERR', e); process.exit(1); });
