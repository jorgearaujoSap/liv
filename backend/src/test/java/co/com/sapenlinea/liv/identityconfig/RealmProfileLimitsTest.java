package co.com.sapenlinea.liv.identityconfig;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.RealKeycloak;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The profile of the realm, applied to a real Keycloak by keycloak-config-cli, refuses what would
 * not fit in the registry of LIV (plan, ajustes de T13): an administrator cannot create a user that
 * would then be refused on its first login with 401 001-14. Accepts the exact limits: a name of 74
 * and a last name of 75 (the name claim, with its space, is 150) and an email of 200.
 */
@DisplayName("Perfil del realm contra Keycloak real (ajustes de T13)")
class RealmProfileLimitsTest {

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Keycloak acepta los largos exactos del registro de LIV")
  void exactLimitsAreAccepted() {
    assertThat(create("Nombre", "Apellido", email(80)).statusCode()).as("control").isEqualTo(201);
    assertThat(create("N".repeat(74), "A".repeat(75), email(200)).statusCode()).isEqualTo(201);
  }

  @Test
  @Tag("001-14")
  @DisplayName(
      "001-14 · Keycloak rechaza un nombre, un apellido o un correo más largos que el registro")
  void longerValuesAreRefused() {
    assertRefused(create("N".repeat(75), "Apellido", email(80)), "firstName");
    assertRefused(create("Nombre", "A".repeat(76), email(80)), "lastName");
    assertRefused(create("Nombre", "Apellido", email(201)), "email");
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Keycloak rechaza un usuario sin nombre o sin apellido")
  void nameIsRequired() {
    assertRefused(create(null, "Apellido", email(80)), "firstName");
    assertRefused(create("Nombre", null, email(80)), "lastName");
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Los espacios alrededor no hacen pasar un nombre más largo que el registro")
  void surroundingSpacesDoNotStretchTheLimit() {
    assertRefused(create("  " + "N".repeat(74) + "  ", "Apellido", email(80)), "firstName");
  }

  /** Refused because of {@code field}, not for any other reason of the request. */
  private static void assertRefused(HttpResponse<String> answer, String field) {
    assertThat(answer.statusCode()).as("estado por %s", field).isEqualTo(400);
    assertThat(answer.body()).as("motivo").contains("\"field\":\"" + field + "\"");
  }

  private static HttpResponse<String> create(String firstName, String lastName, String email) {
    StringBuilder json =
        new StringBuilder("{\"username\":\"perfil-")
            .append(UUID.randomUUID())
            .append("\",\"enabled\":true,\"email\":\"")
            .append(email)
            .append('"');
    if (firstName != null) {
      json.append(",\"firstName\":\"").append(firstName).append('"');
    }
    if (lastName != null) {
      json.append(",\"lastName\":\"").append(lastName).append('"');
    }
    return RealKeycloak.createUserAnswer(json.append('}').toString());
  }

  /**
   * A unique, valid email of exactly {@code length} characters: a local part of 32 (the limit is
   * 64) and a domain of labels of at most 60 (the limit is 63) that makes up the rest.
   */
  private static String email(int length) {
    if (length < 50) {
      throw new IllegalArgumentException("Un correo de prueba de al menos 50 caracteres");
    }
    String local = UUID.randomUUID().toString().replace("-", "");
    String tld = ".invalid";
    int labels = length - local.length() - 1 - tld.length();
    StringBuilder domain = new StringBuilder();
    while (labels > 0) {
      int label = Math.min(60, labels);
      // A label and its dot; the last label takes what is left.
      if (labels > label && labels - label < 2) {
        label = labels - 2;
      }
      domain.append("d".repeat(label));
      labels -= label;
      if (labels > 0) {
        domain.append('.');
        labels--;
      }
    }
    return local + "@" + domain + tld;
  }
}
