// Realistic extension v5.40 entries (exact field names as passwords.js writes them)
const T0 = 1700000000000;
const base = (o) => Object.assign({
  id: 'id-' + Math.random().toString(36).slice(2, 10), title: '', url: '', username: '', mobile: '', password: '', notes: '',
  tags: [], customFields: [], category: 'other', isNote: false, recordType: 'login', folderId: '',
  cardNumber: '', cardholder: '', cardExpiry: '', cardCvv: '', fullName: '', email: '', phone: '', address: '', idNumber: '', idExpiry: '',
  totp: '', strength: 70, starred: false, createdAt: T0, updatedAt: T0 + 5000, passwordHistory: []
}, o);
const entries = [
  base({ id: 'a1', title: 'GitHub', url: 'https://github.com/login', username: 'baba', password: 'Gh#Str0ng!Pass', category: 'work', folderId: 'fold-office', tags: ['dev', 'code'], starred: true, totp: 'JBSWY3DPEHPK3PXP', notes: 'primary account', lastUsedAt: T0 + 9999,
         customFields: [{ k: 'Recovery', v: 'ABCD-1234' }, { k: 'Team', v: 'बाबा' }], passwordHistory: [{ pw: 'old-gh-1', changedAt: T0 - 1000 }, { pw: 'old-gh-0', changedAt: T0 - 9000 }] }),
  base({ id: 'a2', title: 'Instagram', url: 'https://www.instagram.com', username: '', mobile: '9876543210', password: 'ig,pass"word', category: 'social', notes: 'line1\nline2, with comma\n"quoted"' }),
  base({ id: 'a3', title: 'Netflix', url: 'https://netflix.com', username: 'me@x.com', password: 'nf-Pass-9', category: 'personal', totp: 'otpauth://totp/Netflix:me@x.com?secret=GEZDGNBVGY3TQOJQ&issuer=Netflix&digits=6&period=30', folderId: 'fold-fun' }),
  base({ id: 'a4', title: 'HDFC Visa', recordType: 'card', category: 'banking', cardNumber: '4111111111111111', cardholder: 'Baba Sita Ram', cardExpiry: '08/27', cardCvv: '123', folderId: 'fold-bank', notes: 'credit limit 1L' }),
  base({ id: 'a5', title: 'Aadhaar', recordType: 'identity', category: 'personal', fullName: 'Baba Sita Ram', email: 'b@x.com', phone: '9999999999', address: '12, MG Road, Indore', idNumber: '1234 5678 9012', idExpiry: '2035-01-01', folderId: 'fold-gov' }),
  base({ id: 'a6', title: 'Wifi note', recordType: 'note', isNote: true, category: 'other', notes: 'SSID: home\nPass: hunter2\nहिन्दी टेक्स्ट ✓' }),
  base({ id: 'a7', title: 'SBI NetBanking', url: 'https://onlinesbi.sbi', username: 'sbi_user', password: 'S#bi_2026', category: 'banking' }),
];
const folders = [
  { id: 'fold-office', name: 'Office', createdAt: T0, updatedAt: T0 },
  { id: 'fold-fun', name: 'Fun', createdAt: T0, updatedAt: T0 },
  { id: 'fold-bank', name: 'Bank', createdAt: T0, updatedAt: T0 },
  { id: 'fold-gov', name: 'Govt IDs', createdAt: T0, updatedAt: T0 },
  { id: 'fold-empty', name: 'Empty folder', createdAt: T0, updatedAt: T0 },
];
module.exports = { entries, folders, MASTER: 'Māster#Pass9' };
