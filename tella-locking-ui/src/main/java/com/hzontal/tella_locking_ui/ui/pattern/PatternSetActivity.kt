package com.hzontal.tella_locking_ui.ui.pattern

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.hzontal.tella_locking_ui.TellaKeysUI
import com.hzontal.tella_locking_ui.patternlock.PatternUtils
import com.hzontal.tella_locking_ui.patternlock.PatternView
import com.hzontal.tella_locking_ui.patternlock.SetPatternActivity
import org.hzontal.tella.keys.MainKeyStore
import org.hzontal.tella.keys.key.MainKey
import timber.log.Timber
import javax.crypto.spec.PBEKeySpec

class PatternSetActivity : SetPatternActivity() {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onConfirmed() {
        super.onConfirmed()
        Timber.d("** We've finished first MainKey saving - now we need to proceed with application **")
        TellaKeysUI.getCredentialsCallback().onSuccessfulUnlock(this)
        finish()
    }

    override fun getMinPatternSize(): Int {
        return 6
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onSetPattern(pattern: List<PatternView.Cell>) {
        // Here we are storing MainKey for the first time: generate it, wrap and store
        // also, we are going to set active unlocking to be TELLA_PATTERN
        //
        // One remark: we need TELLA_PATTERN and LEGACY_TELLA_PATTERN if we are to change "char[] password"
        // generation from "pattern"
        super.onSetPattern(pattern)
        val mNewPassphrase = PatternUtils.patternToSha1String(pattern)
        // holder.unlockRegistry.setActiveMethod(applicationContext, UnlockRegistry.Method.TELLA_PATTERN)
        // I've put this in LockApp - for holder.unlockRegistry.getActiveConfig(this) to work

        val mainKey = MainKey.generate()
        val keySpec = PBEKeySpec(mNewPassphrase.toCharArray())
        val config = TellaKeysUI.getUnlockRegistry().getActiveConfig(this@PatternSetActivity)

        TellaKeysUI.getMainKeyStore().store(mainKey, config.wrapper, keySpec, object : MainKeyStore.IMainKeyStoreCallback {
            override fun onSuccess(mainKey: MainKey) {
                Timber.d("** MainKey stored: %s **", mainKey)
                // here, we store MainKey in memory -> unlock the app
                TellaKeysUI.getMainKeyHolder().set(mainKey)
            }

            override fun onError(throwable: Throwable) {
                Timber.e(throwable, "** MainKey store error **")
            }
        })
    }
}