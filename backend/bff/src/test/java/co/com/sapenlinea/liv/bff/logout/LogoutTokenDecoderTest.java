package co.com.sapenlinea.liv.bff.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.bff.logout.LogoutTokenDecoder.LogoutToken;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.BadJwtException;

/**
 * Each rule of the logout token (OpenID Connect Back-Channel Logout 1.0, §2.4 and §2.6), one at a
 * time: a token that breaks only that rule is refused. The keys are served by a small server of the
 * test, as the provider publishes them at its {@code jwks_uri}; the clock of the BFF is fixed.
 */
@Tag("001-09")
class LogoutTokenDecoderTest {

  private static final String ISSUER = "https://proveedor.test/realms/liv";
  private static final String CLIENT = "liv-bff";
  private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
  private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

  private static RSAKey key;
  private static HttpServer keys;
  private static LogoutTokenDecoder decoder;

  @BeforeAll
  static void start() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("clave-1").generate();
    byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    keys = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    keys.createContext(
        "/jwks",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          exchange.getResponseBody().write(jwks);
          exchange.close();
        });
    keys.start();
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(OidcRegistrations.USER)
            .clientId(CLIENT)
            .clientSecret("secreto")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/liv")
            .authorizationUri(ISSUER + "/auth")
            .tokenUri(ISSUER + "/token")
            .issuerUri(ISSUER)
            .jwkSetUri("http://localhost:" + keys.getAddress().getPort() + "/jwks")
            .build();
    decoder =
        new LogoutTokenDecoder(
            new InMemoryClientRegistrationRepository(registration),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterAll
  static void stop() {
    keys.stop(0);
  }

  private static JWTClaimsSet.Builder valid() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(List.of(CLIENT))
        .subject("usuario-1")
        .claim("sid", "sid-1")
        .claim("events", Map.of(EVENT, Map.of()))
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(NOW.minusSeconds(5)));
  }

  private static String signed(JOSEObjectType type, Consumer<JWTClaimsSet.Builder> change)
      throws Exception {
    JWTClaimsSet.Builder claims = valid();
    change.accept(claims);
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(type).build(),
            claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static String signed(Consumer<JWTClaimsSet.Builder> change) throws Exception {
    return signed(new JOSEObjectType("logout+jwt"), change);
  }

  @Test
  @DisplayName("001-09 · Acepta el logout token del estándar, con typ logout+jwt o JWT")
  void acceptsTheStandardToken() throws Exception {
    assertThat(decoder.decode(signed(claims -> {}))).isEqualTo(new LogoutToken(ISSUER, "sid-1"));
    assertThat(decoder.decode(signed(JOSEObjectType.JWT, claims -> {})).sid()).isEqualTo("sid-1");
    assertThat(decoder.decode(signed(null, claims -> {})).sid()).isEqualTo("sid-1");
  }

  @Test
  @DisplayName(
      "001-09 · Rechaza el que rompe una regla: typ, iss, aud, iat, exp, events, jti, sid o nonce")
  void refusesEachBrokenRule() throws Exception {
    Map<String, String> broken =
        Map.ofEntries(
            Map.entry("typ", signed(new JOSEObjectType("at+jwt"), claims -> {})),
            Map.entry("iss", signed(claims -> claims.issuer("https://otro.test"))),
            Map.entry("aud", signed(claims -> claims.audience("otro-cliente"))),
            Map.entry(
                "iat viejo",
                signed(claims -> claims.issueTime(Date.from(NOW.minus(Duration.ofMinutes(10)))))),
            Map.entry(
                "iat futuro",
                signed(claims -> claims.issueTime(Date.from(NOW.plus(Duration.ofMinutes(10)))))),
            Map.entry("sin iat", signed(claims -> claims.issueTime(null))),
            Map.entry(
                "exp vencido",
                signed(claims -> claims.expirationTime(Date.from(NOW.minusSeconds(120))))),
            Map.entry("sin events", signed(claims -> claims.claim("events", null))),
            Map.entry(
                "otro evento", signed(claims -> claims.claim("events", Map.of("otro", Map.of())))),
            Map.entry("sin jti", signed(claims -> claims.jwtID(null))),
            Map.entry("sin sid", signed(claims -> claims.claim("sid", null))),
            Map.entry("con nonce", signed(claims -> claims.claim("nonce", "n-1"))));

    broken.forEach((rule, token) ->
        assertThatThrownBy(() -> decoder.decode(token))
            .as(rule)
            .isInstanceOf(BadJwtException.class));
  }

  @Test
  @DisplayName("001-09 · Rechaza la firma HS256, la de otra clave y un JWT sin firma")
  void refusesAnotherSignature() throws Exception {
    SignedJWT hs256 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), valid().build());
    hs256.sign(new MACSigner(new byte[32]));
    RSAKey other = new RSAKeyGenerator(2048).keyID("clave-1").generate();
    SignedJWT otherKey =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("clave-1").build(), valid().build());
    otherKey.sign(new RSASSASigner(other));
    String unsigned =
        java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
            + "."
            + java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(valid().build().toString().getBytes(StandardCharsets.UTF_8))
            + ".";

    for (String token : List.of(hs256.serialize(), otherKey.serialize(), unsigned)) {
      assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }
  }
}
