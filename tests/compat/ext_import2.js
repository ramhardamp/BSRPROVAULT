const fs = require('fs');
const { boot } = require('./load');
const { entries, folders, MASTER } = require('./dataset');
const D = '/home/claude/xfer/';
async function importFile(name, existing, existingFolders) {
  const w = boot(); const R = (c) => w.__vm.runInContext(c, w.__ctx);
  R('globalThis.__entries = ' + JSON.stringify(existing) + '; globalThis.__saved = null; globalThis.__toast="";');
  const ui = R(`new ImportExportUI(() => globalThis.__entries, async (e) => { globalThis.__saved = e; }, (p) => 70, () => 'g' + Math.random().toString(36).slice(2,8), (m) => { globalThis.__toast = m; }, () => {}, () => ${JSON.stringify(existingFolders)}, async (f) => {})`);
  ui._confirmMasterPassword = async () => MASTER; ui._askImportPw = async () => MASTER;
  const t0 = Date.now();
  await ui._loadFile(new w.File([fs.readFileSync(D + name, 'utf8')], name));
  const stat = { toast: R('globalThis.__toast'), parsed: ui.parsedItems.length, dup: ui.parsedItems.filter(i => i._status === 'dup').length, new: ui.parsedItems.filter(i => i._status === 'new').length };
  await ui._doImport();
  stat.saved = JSON.parse(R('JSON.stringify(globalThis.__saved)') || 'null')?.length; stat.ms = Date.now() - t0;
  return stat;
}
(async () => {
  console.log('android_big (2000) -> empty extension vault:', JSON.stringify(await importFile('android_big.vaultbak', [], [])));
  console.log('android_ext into extension that ALREADY has the 7 originals:', JSON.stringify(await importFile('android_ext.vaultbak', entries, folders)));
})().catch(e => { console.log('ERR', e); process.exit(1); });
