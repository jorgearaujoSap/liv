package co.com.sapenlinea.liv.bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The tokens of a session are stored encrypted with AES-GCM (001-11, plan, step 10): whoever reads
 * the database, or a backup, does not read a token, and a changed value is noticed.
 */
@Tag("001-11")
class TokenCipherTest {

  private static final byte[] KEY =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PLAIN =
      "eyJhbGciOiJSUzI1NiJ9.token.firma".getBytes(StandardCharsets.UTF_8);

  private final TokenCipher cipher = new TokenCipher(KEY);

  @Test
  @DisplayName("001-11 · Lo cifrado se descifra con la misma clave y no contiene el texto original")
  void roundTrip() {
    byte[] encrypted = cipher.encrypt(PLAIN);

    assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN);
    assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
        .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
  }

  @Test
  @DisplayName("001-11 · El mismo texto cifrado dos veces da bytes distintos")
  void everyEncryptionUsesANewNonce() {
    assertThat(cipher.encrypt(PLAIN)).isNotEqualTo(cipher.encrypt(PLAIN));
  }

  @Test
  @DisplayName("001-11 · Un byte cambiado, en cualquier posición, hace fallar el descifrado")
  void detectsAChangedByte() {
    byte[] encrypted = cipher.encrypt(PLAIN);
    for (int position = 0; position < encrypted.length; position++) {
      byte[] changed = Arrays.copyOf(encrypted, encrypted.length);
      changed[position] ^= 0x01;
      assertThatThrownBy(() -> cipher.decrypt(changed))
          .as("byte %d cambiado", position)
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  @DisplayName("001-11 · Con otra clave no se descifra")
  void anotherKeyCannotDecrypt() {
    byte[] other = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> new TokenCipher(other).decrypt(cipher.encrypt(PLAIN)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("001-11 · Un valor truncado o vacío no se descifra")
  void refusesShortValues() {
    assertThatThrownBy(() -> cipher.decrypt(new byte[0])).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cipher.decrypt(new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("001-11 · La clave debe ser de 32 bytes, en base64")
  void keyMustBe32Bytes() {
    assertThatThrownBy(() -> new TokenCipher(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32");
    assertThatThrownBy(() -> TokenCipher.fromBase64("no es base64 !!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TokenCipher.fromBase64(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            TokenCipher.fromBase64(Base64.getEncoder().encodeToString(KEY))
                .decrypt(cipher.encrypt(PLAIN)))
        .isEqualTo(PLAIN);
  }

  @Test
  @DisplayName("001-11 · El mensaje de error no incluye la clave ni el texto")
  void errorsDoNotLeakSecrets() {
    byte[] encrypted = cipher.encrypt(PLAIN);
    encrypted[encrypted.length - 1] ^= 0x01;

    assertThatThrownBy(() -> cipher.decrypt(encrypted))
        .satisfies(error ->
            assertThat(String.valueOf(error.getMessage()))
                .doesNotContain("0123456789abcdef")
                .doesNotContain("token"));
  }
}
