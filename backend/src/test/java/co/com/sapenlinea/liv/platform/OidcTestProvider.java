package co.com.sapenlinea.liv.platform;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A generic OIDC provider in a container ({@code mock-oauth2-server}, ADR 0023) that serves the
 * discovery document and the JWKS of issuer {@code liv}, as the API reads them in production
 * (001-20). The signing key is created here and handed to the provider, so each test can sign the
 * exact token it needs: valid, expired, for another audience or issuer, or with another key.
 */
final class OidcTestProvider {

  static final String USER_AUDIENCE = "liv-api";
  static final String SERVICE_AUDIENCE = "liv-internal";
  static final String SERVICE_CLIENT = "liv-bff-service";
  static final String USER_CLIENT = "liv-bff";

  private static final String ISSUER_ID = "liv";
  private static final String IMAGE =
      "ghcr.io/navikt/mock-oauth2-server:3.0.1@sha256:4f3660e5f3586b81fe2e0d42d0c9fcd3c8c64268db0d3ade261dadd70d27020b";
  private static final int PORT = 8080;

  /** The provider renames the key to the issuer id, so the tokens carry that kid. */
  private static final RSAKey KEY = newKey();

  private static final GenericContainer<?> SERVER = start();

  private OidcTestProvider() {}

  static String issuer() {
    return "http://%s:%d/%s".formatted(SERVER.getHost(), SERVER.getMappedPort(PORT), ISSUER_ID);
  }

  /** A valid user access token, as the realm issues it for the BFF (claims at the top level). */
  static String userToken(String subject, UUID administrationId) {
    return sign(user(subject, administrationId).build(), KEY);
  }

  static String userToken(
      String subject, UUID administrationId, UnaryOperator<JWTClaimsSet.Builder> change) {
    return sign(change.apply(user(subject, administrationId)).build(), KEY);
  }

  /** A valid client credentials token of the BFF service client. */
  static String serviceToken() {
    return sign(service().build(), KEY);
  }

  static String serviceToken(UnaryOperator<JWTClaimsSet.Builder> change) {
    return sign(change.apply(service()).build(), KEY);
  }

  /** Same claims and kid, signed with a key the provider does not publish. */
  static String userTokenSignedWithAnotherKey(String subject, UUID administrationId) {
    return sign(user(subject, administrationId).build(), newKey());
  }

  /** Same claims without a signature ({@code alg: none}). */
  static String unsignedUserToken(String subject, UUID administrationId) {
    return new PlainJWT(user(subject, administrationId).build()).serialize();
  }

  static String serviceTokenSignedWithAnotherKey() {
    return sign(service().build(), newKey());
  }

  static String unsignedServiceToken() {
    return new PlainJWT(service().build()).serialize();
  }

  private static JWTClaimsSet.Builder user(String subject, UUID administrationId) {
    return base()
        .subject(subject)
        .audience(USER_AUDIENCE)
        .claim("azp", USER_CLIENT)
        .claim("sid", UUID.randomUUID().toString())
        .claim("administration_id", administrationId.toString())
        .claim("roles", List.of("CONSULTA"));
  }

  private static JWTClaimsSet.Builder service() {
    return base()
        .subject("service-account-" + SERVICE_CLIENT)
        .audience(SERVICE_AUDIENCE)
        .claim("azp", SERVICE_CLIENT);
  }

  private static JWTClaimsSet.Builder base() {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(issuer())
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))));
  }

  private static String sign(JWTClaimsSet claims, RSAKey key) {
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .type(JOSEObjectType.JWT)
                  .keyID(ISSUER_ID)
                  .build(),
              claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("No se pudo firmar el token de prueba", e);
    }
  }

  private static RSAKey newKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(ISSUER_ID).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("No se pudo generar la clave de prueba", e);
    }
  }

  private static GenericContainer<?> start() {
    String config =
        JSONObjectUtils.toJSONString(
            Map.of(
                "interactiveLogin",
                false,
                "tokenProvider",
                Map.of(
                    "keyProvider",
                    Map.of("initialKeys", KEY.toJSONString(), "algorithm", "RS256"))));
    GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withEnv("JSON_CONFIG", config)
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/" + ISSUER_ID + "/.well-known/openid-configuration"));
    container.start();
    return container;
  }
}
