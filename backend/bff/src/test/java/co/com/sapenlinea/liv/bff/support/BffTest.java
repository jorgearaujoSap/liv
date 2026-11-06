package co.com.sapenlinea.liv.bff.support;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import java.sql.SQLException;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The BFF as it runs: production configuration over a real PostgreSQL, a generic OIDC provider
 * (mock-oauth2-server) and a fake liv-api with a fake revocation endpoint (001-20). Every test
 * starts without sessions and without recorded calls. A test picks the issuer of the provider with
 * its own {@code @DynamicPropertySource} that calls {@link #configure}; most extend {@link
 * BffFlowTest}, which uses the usual one.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class BffTest {

  /** A JWT: three base64url parts, the first one a JSON header. */
  protected static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");

  protected static final FakeBackend BACKEND = new FakeBackend();

  @LocalServerPort private int port;

  /** The configuration of the BFF in the tests, for the given issuer of the provider. */
  protected static void configure(DynamicPropertyRegistry registry, Supplier<Object> issuer) {
    registry.add("spring.datasource.url", BffDatabase::jdbcUrl);
    registry.add("spring.datasource.password", () -> BffDatabase.password(BffDatabase.APP));
    registry.add("spring.flyway.password", () -> BffDatabase.password(BffDatabase.OWNER));
    registry.add("liv.bff.oidc.issuer-uri", issuer);
    registry.add("liv.bff.oidc.allow-http-issuer", () -> "true");
    registry.add("liv.bff.oidc.client-secret", () -> "secreto-de-prueba-del-bff");
    registry.add("liv.bff.oidc.service-client-secret", () -> "secreto-de-prueba-del-servicio");
    registry.add("liv.bff.oidc.revocation-uri", BACKEND::revocationUri);
    registry.add("liv.bff.api.base-url", BACKEND::apiBaseUrl);
    registry.add(
        "liv.bff.session.encryption-key",
        () -> Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
  }

  @BeforeEach
  void startClean() throws SQLException {
    BACKEND.reset();
    BffDatabase.execute(BffDatabase.jdbcUrl(), BffDatabase.OWNER, "DELETE FROM bff.spring_session");
    BffDatabase.execute(
        BffDatabase.jdbcUrl(),
        BffDatabase.OWNER,
        "DO $$ BEGIN IF to_regclass('bff.oidc_session') IS NOT NULL THEN"
            + " DELETE FROM bff.oidc_session; END IF; END $$");
  }

  /** Waits: the tests of the session limits run on real time, with limits of a few seconds. */
  protected static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** A number of the JSON that {@code /bff/session} answers. */
  protected static long seconds(String json, String field) {
    java.util.regex.Matcher matcher =
        Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
    org.assertj.core.api.Assertions.assertThat(matcher.find()).as("%s en %s", field, json).isTrue();
    return Long.parseLong(matcher.group(1));
  }

  /** What the token endpoint of the provider answers to a renewal, in the tests that replace it. */
  protected static org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
      renewed(String accessToken) {
    return org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse.withToken(
            accessToken)
        .tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER)
        .expiresIn(300)
        .refreshToken("refresco-renovado")
        .build();
  }

  protected String bffBase() {
    return "http://localhost:" + port;
  }

  protected Browser browser() {
    return new Browser(bffBase());
  }

  /** How many sessions the BFF keeps in its database. */
  protected static int storedSessions() throws SQLException {
    return Integer.parseInt(
        BffDatabase.queryValue(
            BffDatabase.jdbcUrl(), BffDatabase.OWNER, "SELECT count(*) FROM bff.spring_session"));
  }
}
