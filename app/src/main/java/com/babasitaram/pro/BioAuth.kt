package com.babasitaram.pro

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/**
 * Biometric settings ek jagah.
 *
 * FIX (Xiaomi/Redmi/POCO MIUI/HyperOS): pehle BIOMETRIC_STRONG maanga jaata tha. In phones par
 * fingerprint/face aksar "WEAK" class ke hote hain, isliye prompt mein sirf Pattern/PIN chalta tha
 * aur fingerprint/face kaam nahi karte the (Samsung par STRONG hone se theek chalta tha).
 * Ab BIOMETRIC_WEAK maangte hain. (Master Password Keystore mein rakhi hai, isliye WEAK kaafi hai.)
 *
 * API 28-29 par BIOMETRIC + DEVICE_CREDENTIAL ka combination supported nahi hai, wahan sirf
 * biometric + "Master Password" negative button use hota hai.
 */
object BioAuth {
    fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30)
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canUse(ctx: Context): Boolean = try {
        BiometricManager.from(ctx).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) { false }

    fun promptInfo(title: String, subtitle: String): BiometricPrompt.PromptInfo {
        val b = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators())
        // DEVICE_CREDENTIAL ke saath negative button nahi lag sakta; uske bina zaroori hai.
        if (Build.VERSION.SDK_INT < 30) b.setNegativeButtonText("Master Password use karein")
        return b.build()
    }
}
