package co.com.sapenlinea.liv.bff.support;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The BFF as it runs: production configuration over a real PostgreSQL, a generic OIDC provider
 * (mock-oauth2-server) and a fake liv-api with a fake revocation endpoint (001-20). Every test
 * starts without sessions and without recorded calls.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class BffFlowTest {

  /** A JWT: three base64url parts, the first one a JSON header. */
  protected static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");

  protected static final FakeBackend BACKEND = new FakeBackend();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", BffDatabase::jdbcUrl);
    registry.add("spring.datasource.password", () -> BffDatabase.password(BffDatabase.APP));
    registry.add("spring.flyway.password", () -> BffDatabase.password(BffDatabase.OWNER));
    registry.add("liv.bff.oidc.issuer-uri", OidcProvider::issuer);
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
