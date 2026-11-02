package co.com.sapenlinea.liv.platform;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API with its production configuration, without replacing beans (plan, 001-14 and 001-20): a
 * real PostgreSQL with the {@code liv_app} role and a generic OIDC provider. The probe controllers
 * exist only here, to observe what the security filters leave for the controllers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PlatformWebTest.ApiProbe.class, PlatformWebTest.InternalProbe.class})
abstract class PlatformWebTest {

  static final String ACTIVE = "api-activo";
  static final String INACTIVE = "api-inactivo";
  static final String UNREGISTERED = "api-sin-registro";

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static {
    seedUsers();
  }

  @LocalServerPort int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
    registry.add("spring.datasource.password", () -> LivDatabase.password(LivDatabase.APP));
    registry.add("liv.security.issuer-uri", OidcTestProvider::issuer);
  }

  HttpResponse<String> get(String path, Map<String, String> headers) {
    return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
  }

  HttpResponse<String> post(String path, Map<String, String> headers) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}")),
        headers);
  }

  static Map<String, String> bearer(String token) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    return headers;
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static HttpResponse<String> send(
      HttpRequest.Builder request, Map<String, String> headers) {
    headers.forEach(request::header);
    try {
      return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Users of the generic provider: one active in A, one deactivated in A. */
  private static void seedUsers() {
    String issuer = OidcTestProvider.issuer();
    insertUser(UUID.randomUUID(), LivDatabase.ADMIN_A, issuer, ACTIVE, "ACTIVE");
    insertUser(UUID.randomUUID(), LivDatabase.ADMIN_A, issuer, INACTIVE, "INACTIVE");
  }

  private static void insertUser(
      UUID id, UUID administration, String issuer, String subject, String status) {
    try {
      LivDatabase.update(
          LivDatabase.OWNER,
          null,
          """
          INSERT INTO core.admin_user (id, administration_id, full_name, email, status,
                                       identity_issuer, identity_subject)
          VALUES (?, ?, 'Usuario de prueba', 'prueba@test.invalid', ?, ?, ?)
          """,
          id,
          administration,
          status,
          issuer,
          subject);
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("No se pudo sembrar el usuario " + subject, e);
    }
  }

  /** What a controller of /api/** sees after the filters. */
  @RestController
  static class ApiProbe {
    @GetMapping("/api/v1/probe")
    Map<String, String> probe() {
      CurrentUser user = CurrentUser.current().orElseThrow();
      Map<String, String> seen = new LinkedHashMap<>();
      seen.put("userId", user.userId().toString());
      seen.put("subject", user.subject());
      seen.put("administrationId", AdministrationContext.current().orElseThrow().toString());
      seen.put("correlationId", MDC.get("correlation_id"));
      return seen;
    }
  }

  /** The internal routes of the BFF, with the user token the filter validated. */
  @RestController
  static class InternalProbe {
    @PostMapping("/internal/v1/sessions")
    ResponseEntity<Map<String, String>> start() {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(Map.of("subject", VerifiedUserToken.current().orElseThrow().getSubject()));
    }

    @PostMapping("/internal/v1/sessions/{sid}/end")
    ResponseEntity<Void> end(@PathVariable String sid) {
      return ResponseEntity.noContent().build();
    }
  }
}
