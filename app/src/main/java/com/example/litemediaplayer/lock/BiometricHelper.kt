package com.example.litemediaplayer.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor() {

    fun canUseBiometric(
        context: Context,
        authMethod: LockAuthMethod = LockAuthMethod.BIOMETRIC
    ): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(
            allowedAuthenticators(authMethod)
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        authMethod: LockAuthMethod = LockAuthMethod.BIOMETRIC,
        onResult: (Boolean) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                onResult(false)
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val allowedAuthenticators = allowedAuthenticators(authMethod)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)

        if (requiresNegativeButton(allowedAuthenticators)) {
            promptInfoBuilder.setNegativeButtonText("キャンセル")
        }

        val promptInfo = promptInfoBuilder.build()

        prompt.authenticate(promptInfo)
    }

    private fun requiresNegativeButton(allowedAuthenticators: Int): Boolean {
        return allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0
    }

    private fun allowedAuthenticators(authMethod: LockAuthMethod): Int {
        return when (authMethod) {
            LockAuthMethod.BIOMETRIC,
            LockAuthMethod.FACE -> {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            }

            LockAuthMethod.PIN,
            LockAuthMethod.PATTERN -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }
}
