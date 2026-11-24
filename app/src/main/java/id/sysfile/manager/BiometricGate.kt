package id.sysfile.manager

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Gerbang biometrik untuk Folder Terkunci: memakai sidik jari atau kredensial
// layar kunci (PIN/pola/sandi) milik perangkat, tanpa kata sandi buatan sendiri.

object BiometricGate {

    /** Tampilkan prompt biometrik; [onSuccess] dipanggil di utas utama bila lolos. */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        unavailableMessage: String = "Kunci layar / biometrik perangkat belum diatur.",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onError(unavailableMessage)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
