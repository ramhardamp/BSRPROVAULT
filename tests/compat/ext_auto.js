const fs = require('fs');
const { boot } = require('./load');
const { entries, folders, MASTER } = require('./dataset');
(async () => {
  const w = boot(); const R = (c) => w.__vm.runInContext(c, w.__ctx);
  w.__m = MASTER;
  // Extension ka raw stored vault: entries + folder meta records + activity log records (jaisa storage mein hota hai)
  const raw = [...entries, ...folders.map(f => ({ _metaType: 'folder', ...f })), { _metaType: 'activityLog', id: 'log1', items: [{ t: 1, a: 'unlock' }] }];
  w.__raw = JSON.stringify({ version: '2.0', app: 'Vault', exportDate: new Date().toISOString(), count: raw.length, entries: raw });
  // REAL VaultCrypto.encrypt (crypto.js) + REAL wrapper format from background.js
  const enc = await R('VaultCrypto.encrypt(globalThis.__raw, globalThis.__m)');
  fs.writeFileSync('/home/claude/xfer/ext_auto.vaultbak', JSON.stringify({ vault_backup: true, v: 2, data: enc }));
  console.log('auto-backup written', enc.length);
  // 2000-entry vault through the real manual export
  const big = []; for (let i = 0; i < 2000; i++) big.push({ ...entries[i % 3], id: 'b' + i, title: 'Site ' + i, url: 'https://site' + i + '.com', username: 'user' + i, password: 'Pw#' + i + 'xYz!' , notes: 'note ' + i, passwordHistory: [], customFields: [], tags: [] });
  const bigPayload = JSON.stringify({ version: '2.0', app: 'BABASITARAMPro', exportDate: new Date().toISOString(), count: big.length, entries: big, folders });
  fs.writeFileSync('/home/claude/xfer/ext_big.vaultbak', await R('encryptBackup(' + JSON.stringify(bigPayload) + ', globalThis.__m)'));
  console.log('big backup written');
})().catch(e => { console.log('ERR', e); process.exit(1); });
