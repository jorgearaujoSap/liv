package co.com.sapenlinea.liv.bff.session;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts what the session stores about the tokens (001-11, plan, step 10): AES-256-GCM, a random
 * nonce for every value and an authentication tag, so whoever reads the database or a backup does
 * not read a token and a changed value is noticed. The key comes from the environment (in AWS,
 * Secrets Manager) and never from the repository.
 *
 * <p>The value is {@code version (1 byte) | nonce (12 bytes) | ciphertext and tag}. The version
 * leaves room for a new format or a key rotation.
 */
public final class TokenCipher {

  private static final byte VERSION = 1;
  private static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final byte[] CONTEXT =
      "liv-bff/session-tokens/v1".getBytes(StandardCharsets.UTF_8);

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(byte[] key) {
    if (key == null || key.length != KEY_BYTES) {
      throw new IllegalArgumentException(
          "La clave de cifrado de la sesión debe tener " + KEY_BYTES + " bytes");
    }
    this.key = new SecretKeySpec(key, "AES");
  }

  /** The key as it arrives in the configuration: 32 bytes in base64. */
  public static TokenCipher fromBase64(String encoded) {
    if (encoded == null) {
      throw new IllegalArgumentException("Falta la clave de cifrado de la sesión");
    }
    return new TokenCipher(Base64.getDecoder().decode(encoded.trim()));
  }

  public byte[] encrypt(byte[] plain) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(CONTEXT);
      byte[] encrypted = cipher.doFinal(plain);
      return ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.length)
          .put(VERSION)
          .put(nonce)
          .put(encrypted)
          .array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("No se pudo cifrar los datos de la sesión", e);
    }
  }

  public byte[] decrypt(byte[] stored) {
    if (stored == null || stored.length <= 1 + NONCE_BYTES || stored[0] != VERSION) {
      throw unreadable(null);
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, NONCE_BYTES));
      cipher.updateAAD(CONTEXT);
      return cipher.doFinal(stored, 1 + NONCE_BYTES, stored.length - 1 - NONCE_BYTES);
    } catch (GeneralSecurityException e) {
      throw unreadable(e);
    }
  }

  private static IllegalStateException unreadable(Exception cause) {
    return new IllegalStateException("Los datos cifrados de la sesión no se pueden leer", cause);
  }
}
