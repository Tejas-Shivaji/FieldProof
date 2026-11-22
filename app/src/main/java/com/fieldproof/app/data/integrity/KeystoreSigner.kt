package com.fieldproof.app.data.integrity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.Certificate

/**
 * Signs the raw SHA-256 digest with an Android Keystore EC key.
 * If SQLite is edited but the key is not available to the attacker,
 * a forged digest will fail signature verification.
 *
 * This still does not prove real-world authenticity of the photo.
 */
class KeystoreSigner(
    private val alias: String = DEFAULT_ALIAS,
) {
    fun signDigest(digest: ByteArray): String {
        ensureKey()
        val signature = Signature.getInstance("SHA256withECDSA")
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
        signature.initSign(privateKey)
        signature.update(digest)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun verifyDigest(digest: ByteArray, signatureBase64: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val cert: Certificate = keyStore.getCertificate(alias) ?: return false
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(cert)
            signature.update(digest)
            signature.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) return
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "fieldproof_evidence_v1"
    }
}
