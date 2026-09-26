package com.babasitaram.pro

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class BSRApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val themePref = AppPrefs.getTheme(this)
        val appCompatMode = when (themePref) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(appCompatMode)
        Log.i(
            "BSR_Theme",
            "startup pref=" + themePref +
                " appCompatMode=" + appCompatMode +
                " uiNight=" + (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        )

        // Background worker failures must not terminate the whole app process.
        // Main-thread failures still use Android's normal fatal-exception handling.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BSRApp", "Unhandled exception on " + thread.name + ": " + throwable.javaClass.name, throwable)
            if (thread == Looper.getMainLooper().thread) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                // Screenshot, screen-recording aur recent-apps preview se bachao
                if (AppPrefs.getSecureScreen(a)) {
                    a.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })

        // App-level lifecycle only: Activity-to-Activity navigation must NOT reset the timer.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AppPrefs.setLastActive(this@BSRApp)
            }

            override fun onStart(owner: LifecycleOwner) {
                if (VaultManager.isUnlocked && AppPrefs.isSessionExpired(this@BSRApp)) {
                    VaultManager.lock()
                }
            }
        })
    }
}
