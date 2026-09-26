# Compatibility tests (reference)
Requirements: Node 20+, `npm i jsdom`, Kotlin compiler + JDK, Android `android.jar`, Gson.
1. Edit `EXT` path in `load.js` to the extension's `CHROME` folder.
2. `node ext_export.js` (extension real export → files), `node ext_auto.js` (auto-backup + 2000 entries).
3. Compile/run `ExtToAndroid.kt`, `AndroidExports.kt`, `AutoBackupAndBig.kt`, `AndroidUnitTests.kt` against the app classes.
4. `node ext_import.js`, `node ext_import2.js` (Android files → extension's real importer).
