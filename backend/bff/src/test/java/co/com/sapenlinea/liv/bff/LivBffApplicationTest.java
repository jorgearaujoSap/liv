package co.com.sapenlinea.liv.bff;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spike T16: Spring Cloud Gateway Server MVC funciona con Spring Boot 4.1. El BFF arranca sin base
 * de datos (la conexión es perezosa) y enruta una ruta de prueba hacia un servidor de juguete. Las
 * rutas reales (/api/** con TokenRelay) llegan con las tareas siguientes.
 */
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:postgresql://localhost:1/sin-base",
      "spring.datasource.password=sin-uso",
      "spring.flyway.enabled=false"
    })
@Import(LivBffApplicationTest.OpenSpikeRoute.class)
class LivBffApplicationTest {

  private static final HttpServer UPSTREAM = startUpstream();

  /**
   * The BFF now asks for a session on every route but its own sign in (T17); the Gateway spike runs
   * on a route of its own that this test opens. The real routes (/api/**) arrive with T18.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class OpenSpikeRoute {
    @Bean
    @Order(0)
    SecurityFilterChain spikeChain(HttpSecurity http) {
      return http.securityMatcher("/spike/**")
          .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
          .csrf(csrf -> csrf.disable())
          .build();
    }
  }

  @LocalServerPort private int port;

  /**
   * Toda la ruta en una sola fuente de propiedades: Spring Boot enlaza una lista desde una sola.
   */
  @DynamicPropertySource
  static void upstream(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "spike");
    registry.add(
        "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]", () -> "Path=/spike/**");
    registry.add(
        "spring.cloud.gateway.server.webmvc.routes[0].uri",
        () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
  }

  @AfterAll
  static void stopUpstream() {
    UPSTREAM.stop(0);
  }

  @Test
  @DisplayName("El BFF arranca sin base de datos y la puerta de entrada enruta hacia el destino")
  void gatewayRoutesToTheUpstream() throws IOException, InterruptedException {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spike/ping"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("destino /spike/ping");
  }

  private static HttpServer startUpstream() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            byte[] body =
                ("destino " + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
