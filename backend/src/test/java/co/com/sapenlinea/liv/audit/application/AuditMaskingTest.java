package co.com.sapenlinea.liv.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What the audit keeps of the personal data of the provider's events (001-18, plan step 9). */
@DisplayName("Enmascarado de la auditoría (T15)")
class AuditMaskingTest {

  private static final String KEY = "clave-hmac-de-prueba-con-mas-de-32-caracteres";

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(
      nullValues = "NULO",
      value = {
        "203.0.113.77, 203.0.113.0/24",
        "10.1.2.3, 10.1.2.0/24",
        "2001:db8:abcd:12:0:0:0:1, 2001:db8:abcd::/48",
        "2001:db8:abcd:12::1, 2001:db8:abcd::/48",
        "::1, ::/48",
        "no-es-una-ip, NULO",
        "999.1.1.1, NULO",
        "NULO, NULO"
      })
  @Tag("001-18")
  @DisplayName(
      "001-18 · La IP se trunca a /24 en IPv4 y a /48 en IPv6; lo que no es IP no se guarda")
  void truncatesAddresses(String ip, String truncated) {
    assertThat(AuditMasking.truncateIp(ip)).isEqualTo(truncated);
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · El usuario intentado queda como HMAC del nombre normalizado, con su longitud")
  void masksTheAttemptedUsername() {
    AuditMasking masking = new AuditMasking(KEY);

    Map<String, Object> masked = masking.maskUsername("  Juan.Perez ");

    assertThat(masked).containsOnlyKeys("hmac", "length").containsEntry("length", 10);
    assertThat(masking.maskUsername("juan.perez")).isEqualTo(masked);
    assertThat((String) masked.get("hmac")).matches("[0-9a-f]{64}");
    assertThat(new AuditMasking(KEY + "-otra").maskUsername("juan.perez")).isNotEqualTo(masked);
    assertThat(masking.maskUsername(null)).isNull();
    assertThat(masking.maskUsername("   ")).isNull();
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · La clave del HMAC tiene al menos 32 caracteres")
  void keyMustBeLongEnough() {
    assertThatThrownBy(() -> new AuditMasking("corta"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LIV_AUDIT_HMAC_KEY");
    assertThatThrownBy(() -> new AuditMasking(null)).isInstanceOf(IllegalStateException.class);
  }
}
