package co.com.sapenlinea.liv.platform;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API with its production configuration, without replacing beans (plan, 001-14 and 001-20): a
 * real PostgreSQL with the {@code liv_app} role and a generic OIDC provider. The probe controllers
 * exist only here, to observe what the security filters leave for the controllers. Public for the
 * tests of other modules (core) that need the same API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PlatformWebTest.ApiProbe.class, PlatformWebTest.PermissionProbe.class})
public abstract class PlatformWebTest {

  protected static final String ACTIVE = "api-activo";
  protected static final String INACTIVE = "api-inactivo";
  protected static final String UNREGISTERED = "api-sin-registro";

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  static {
    seedUsers();
  }

  @LocalServerPort protected int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
    registry.add("spring.datasource.password", LivDatabase::appPassword);
    registry.add("liv.security.issuer-uri", OidcTestProvider::issuer);
  }

  protected HttpResponse<String> get(String path, Map<String, String> headers) {
    return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
  }

  protected HttpResponse<String> post(String path, Map<String, String> headers) {
    return post(path, headers, "{}");
  }

  protected HttpResponse<String> post(String path, Map<String, String> headers, String body) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)),
        headers);
  }

  protected static Map<String, String> bearer(String token) {
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
    request.timeout(TIMEOUT);
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
    LivDatabase.registerUser(LivDatabase.ADMIN_A, issuer, ACTIVE, "ACTIVE");
    LivDatabase.registerUser(LivDatabase.ADMIN_A, issuer, INACTIVE, "INACTIVE");
  }

  /** A use case that exists only in the tests, guarded like the real ones (001-16). */
  @Service
  public static class PermissionProbe {

    private final AtomicInteger runs = new AtomicInteger();

    @RequiresPermission("core.user.read")
    public String run() {
      runs.incrementAndGet();
      return "ejecutado";
    }

    /** How many times {@link #run} got past the permission check. */
    public int runs() {
      return runs.get();
    }
  }

  /** What a controller of /api/** sees after the filters. */
  @RestController
  static class ApiProbe {

    private final PermissionProbe permissionProbe;

    ApiProbe(PermissionProbe permissionProbe) {
      this.permissionProbe = permissionProbe;
    }

    @GetMapping("/api/v1/probe/permission")
    Map<String, String> permission() {
      return Map.of("result", permissionProbe.run());
    }

    @GetMapping("/api/v1/probe")
    Map<String, String> probe() {
      CurrentUser user = CurrentUser.current().orElseThrow();
      Map<String, String> seen = new LinkedHashMap<>();
      seen.put("userId", user.userId().toString());
      seen.put("subject", user.subject());
      seen.put("roles", String.join(",", new TreeSet<>(user.roles())));
      seen.put("administrationId", AdministrationContext.current().orElseThrow().toString());
      seen.put("correlationId", MDC.get("correlation_id"));
      return seen;
    }

    @GetMapping("/api/v1/probe/failure")
    Map<String, String> failure() {
      throw new IllegalStateException("detalle interno de la falla");
    }

    @GetMapping("/api/v1/probe/integrity")
    Map<String, String> integrity() {
      throw new DataIntegrityViolationException("violación de la FK de otra administración");
    }

    @GetMapping("/api/v1/probe/unavailable")
    Map<String, String> unavailable() {
      throw new DataAccessResourceFailureException("base de datos caída");
    }
  }
}
