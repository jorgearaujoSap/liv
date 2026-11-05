package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /bff/session} tells the frontend how long the session has left (001-22, 001-10): the
 * seconds until it expires from inactivity (15 minutes) and until it reaches its maximum duration
 * (8 hours). It answers 401 without a valid session and gives nothing else away.
 */
class SessionStatusTest extends BffFlowTest {

  static long seconds(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
    assertThat(matcher.find()).as("%s en %s", field, json).isTrue();
    return Long.parseLong(matcher.group(1));
  }

  @Test
  @Tag("001-22")
  @DisplayName(
      "001-22 · Con sesión informa los segundos que faltan por inactividad (15 min) y por duración máxima (8 h)")
  void reportsTheTimeLeft() {
    Browser browser = browser();
    browser.signIn("/");

    Reply reply = browser.get(bffBase() + "/bff/session");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("content-type")).startsWith("application/json");
    assertThat(seconds(reply.body(), "idleSecondsLeft")).isBetween(880L, 900L);
    assertThat(seconds(reply.body(), "absoluteSecondsLeft")).isBetween(28_780L, 28_800L);
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · La respuesta no se guarda en caché y no lleva ningún token")
  void isNotCachedAndCarriesNoToken() {
    Browser browser = browser();
    browser.signIn("/");

    Reply reply = browser.get(bffBase() + "/bff/session");

    assertThat(reply.header("cache-control")).contains("no-store");
    assertThat(JWT.matcher(reply.headers().toString() + reply.body()).find()).isFalse();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Sin sesión, o con una cookie que no es de una sesión, responde 401 sin datos")
  void withoutASessionIsUnauthorized() {
    Reply without = browser().get(bffBase() + "/bff/session");
    Browser stale = browser();
    stale.setCookie("LIV_SESSION", "MDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAw");
    Reply unknown = stale.get(bffBase() + "/bff/session");

    for (Reply reply : new Reply[] {without, unknown}) {
      assertThat(reply.status()).isEqualTo(401);
      assertThat(reply.body()).doesNotContain("SecondsLeft");
      assertThat(reply.header("cache-control")).contains("no-store");
    }
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Una sesión iniciada pero no autenticada (solo /bff/login) no es una sesión")
  void aSessionWithoutAPersonIsNotASession() {
    Browser browser = browser();
    browser.get(bffBase() + "/bff/login");

    Reply reply = browser.get(bffBase() + "/bff/session");

    assertThat(reply.status()).isEqualTo(401);
  }
}
