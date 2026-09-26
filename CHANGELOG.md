# BSR Vault v6.7.6

## Bug fixes
- Exclude BSR Vault's own package from Autofill fill requests.
- Harden direct vault-entry opening against malformed optional history/custom-field data.
- Harden SAF auto-backup file creation and existing-backup reading.
- Fail clearly when an imported backup URI cannot be opened or is empty.
- Make App Picker resolve the real application label before falling back to package text.
- Polish the main password search bar sizing/spacing.
- Preserve the existing DayNight theme architecture; verified the Light/Dark/System wiring is present.
- Preserve Chrome/JioPOS Autofill matching and SaveInfo behavior.

## Backup behavior
- Selecting an Auto-backup folder persists the SAF tree permission.
- If the selected folder already contains the managed BSR auto-backup, the app offers Restore/Merge.
- After restore/merge, the same selected folder is updated with the current encrypted vault backup.
- New vault writes continue to schedule encrypted auto-backup when a folder is configured.

## Security
- No crypto, vault JSON format, master-password verifier, or browser/eTLD+1 matching changes.
- BSR's own Add/Edit screen is excluded from Autofill suggestions.
