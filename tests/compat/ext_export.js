const fs = require('fs');
const { boot } = require('./load');
const { entries, folders, MASTER } = require('./dataset');
(async () => {
  const w = boot();
  const R = (code) => w.__vm.runInContext(code, w.__ctx);
  R('globalThis.__dl = null; downloadFile = function(content, filename, mime){ globalThis.__dl = {content, filename, mime}; };');
  w.__entries = JSON.parse(JSON.stringify(entries)); w.__folders = JSON.parse(JSON.stringify(folders));
  const ui = R(`new ImportExportUI(() => globalThis.__entries, async (e) => { globalThis.__entries = e; }, (p) => 70, () => 'x' + Math.random(), (m, err) => { globalThis.__toast = (err ? 'ERR ' : '') + m; }, () => {}, () => globalThis.__folders, async (f) => { globalThis.__folders = f; })`);
  ui._confirmMasterPassword = async () => MASTER;
  const out = {};
  for (const type of ['vaultbak', 'json', 'csv', 'vault', 'chrome', 'bitwarden', 'lastpass', 'keepass', 'plain-json']) {
    ui._lastExportTime = 0;
    R('globalThis.__dl = null');
    await ui._doExport(type);
    const dl = w.__dl || R('globalThis.__dl');
    if (!dl) { console.log('NO DOWNLOAD for', type, w.__toast); continue; }
    const fn = 'ext_' + type + '.' + dl.filename.split('.').pop();
    fs.writeFileSync('/home/claude/xfer/' + fn, dl.content);
    console.log('exported', type, '->', fn, dl.content.length + ' bytes');
  }
  fs.writeFileSync('/home/claude/xfer/source.json', JSON.stringify({ entries, folders, master: MASTER }));
})().catch(e => { console.log('ERR', e); process.exit(1); });
