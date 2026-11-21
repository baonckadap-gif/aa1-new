package com.example.data.backup

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedBackupPassphraseRequiredException(
  message: String = "File backup ini terenkripsi AES-256-GCM. Masukkan kata sandi untuk membukanya."
) : IllegalStateException(message)

class BackupDecryptionFailedException(
  message: String = "Kata sandi salah atau file backup terenkripsi tidak valid.",
  cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Mengelola enkripsi & dekripsi AES-256-GCM dengan derivasi kunci PBKDF2WithHmacSHA256
 * untuk melindungi file backup JSON & arsip ZIP SoundPad Pro.
 */
object BackupCryptoManager {

  const val FORMAT_MARKER = "SOUNDPAD_AES_GCM_V1"
  private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
  private const val DEFAULT_ITERATIONS = 100_000
  private const val KEY_LENGTH_BITS = 256
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val GCM_TAG_BITS = 128

  private val secureRandom = SecureRandom()

  fun isEncryptedPayload(rawText: String): Boolean {
    val trimmed = rawText.trimStart()
    if (!trimmed.startsWith("{")) return false
    return runCatching {
      val obj = JSONObject(trimmed)
      obj.optString("format", "") == FORMAT_MARKER &&
        obj.optString("ciphertextB64", "").isNotBlank() &&
        obj.optString("saltB64", "").isNotBlank() &&
        obj.optString("ivB64", "").isNotBlank()
    }.getOrDefault(false)
  }

  fun encryptText(plainText: String, passphrase: String, iterations: Int = DEFAULT_ITERATIONS): String {
    require(passphrase.isNotBlank()) { "Kata sandi enkripsi tidak boleh kosong." }
    val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
    val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
    val keySpec = deriveAesKey(passphrase, salt, iterations)

    val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
    val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

    return JSONObject().apply {
      put("format", FORMAT_MARKER)
      put("kdf", KDF_ALGORITHM)
      put("iterations", iterations)
      put("saltB64", Base64.encodeToString(salt, Base64.NO_WRAP))
      put("ivB64", Base64.encodeToString(iv, Base64.NO_WRAP))
      put("ciphertextB64", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
    }.toString(2)
  }

  fun decryptText(encryptedEnvelopeJson: String, passphrase: String): String {
    if (passphrase.isBlank()) {
      throw EncryptedBackupPassphraseRequiredException()
    }
    return try {
      val obj = JSONObject(encryptedEnvelopeJson)
      require(obj.optString("format", "") == FORMAT_MARKER) {
        "Format enkripsi tidak dikenali."
      }
      val iterations = obj.optInt("iterations", DEFAULT_ITERATIONS).coerceAtLeast(10_000)
      val salt = Base64.decode(obj.getString("saltB64"), Base64.DEFAULT)
      val iv = Base64.decode(obj.getString("ivB64"), Base64.DEFAULT)
      val ciphertext = Base64.decode(obj.getString("ciphertextB64"), Base64.DEFAULT)
      val keySpec = deriveAesKey(passphrase, salt, iterations)

      val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
      cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
      val plainBytes = cipher.doFinal(ciphertext)
      String(plainBytes, StandardCharsets.UTF_8)
    } catch (ce: EncryptedBackupPassphraseRequiredException) {
      throw ce
    } catch (t: Throwable) {
      throw BackupDecryptionFailedException(
        message = "Gagal mendekripsi backup. Pastikan kata sandi benar.",
        cause = t
      )
    }
  }

  private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
    return try {
      val secret = factory.generateSecret(spec)
      SecretKeySpec(secret.encoded, "AES")
    } finally {
      spec.clearPassword()
    }
  }
}
