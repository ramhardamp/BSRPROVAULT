package com.babasitaram.pro

/**
 * Write-layer export guard. Every sensitive export (BSRPRO.Vault, CSV, Chrome CSV, ...) builder in
 * [BackupManager] calls [require]; the grant is issued ONLY by [ExportSecurity.requireMasterPassword]
 * right after VaultManager.verifyMaster() succeeded, and is revoked as soon as the file was written.
 * So calling a builder directly (UI bypass / other code path) without a fresh Master Password
 * verification throws instead of producing a file.
 */
object ExportGate {
    private const val TTL_MS = 5 * 60_000L           // enough for the system file-picker step
    private var grantUntil = 0L
    @Synchronized internal fun issue() { grantUntil = android.os.SystemClock.elapsedRealtime() + TTL_MS }
    @Synchronized fun revoke() { grantUntil = 0L }
    @Synchronized fun isValid(): Boolean = grantUntil != 0L && android.os.SystemClock.elapsedRealtime() < grantUntil
    @Synchronized fun require() {
        if (!isValid()) throw SecurityException("Master Password verification zaroori hai — export dobara shuru karein.")
    }
}
