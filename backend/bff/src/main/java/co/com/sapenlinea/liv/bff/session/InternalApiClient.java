package co.com.sapenlinea.liv.bff.session;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The internal endpoints of liv-api that only the BFF calls (contract core-internal.yaml, ADR
 * 0025). Both go with the client credentials token of {@code liv-bff-service}; StartSession also
 * carries the access token of the person in {@code X-User-Token}. No token reaches a log or an
 * exception message, and nothing a call raises escapes as is: the callers decide what a failure
 * means for the person.
 */
public final class InternalApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(InternalApiClient.class);

  private final RestClient http;
  private final ServiceTokens serviceTokens;

  public InternalApiClient(RestClient http, ServiceTokens serviceTokens) {
    this.http = http;
    this.serviceTokens = serviceTokens;
  }

  /**
   * StartSession: LIV registers the session of the person who just authenticated. A 200 means the
   * session was already created (a retry) and counts as a success.
   *
   * @throws SessionNotStartedException if LIV refuses, fails or does not answer, or the service
   *     token cannot be obtained
   */
  public void startSession(String userAccessToken, String sessionId) {
    HttpStatusCode status;
    try {
      status =
          http.post()
              .uri("/internal/v1/sessions")
              .headers(headers -> {
                headers.setBearerAuth(serviceTokens.accessToken());
                headers.set("X-User-Token", userAccessToken);
              })
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("sid", sessionId))
              .retrieve()
              .toBodilessEntity()
              .getStatusCode();
    } catch (RestClientResponseException e) {
      // LIV answered: a refusal (4xx) is clear; a failure of its own (5xx) leaves it unknown.
      throw new SessionNotStartedException(
          "StartSession respondió " + e.getStatusCode().value(),
          e.getStatusCode().is5xxServerError());
    } catch (ResourceAccessException e) {
      // The exception of a failed connection can name the host: only its class is logged.
      LOG.warn("StartSession no obtuvo respuesta: {}", e.getCause().getClass().getSimpleName());
      throw new SessionNotStartedException("StartSession no obtuvo respuesta", true);
    } catch (RuntimeException e) {
      // The call did not even leave: no service token (the provider refused it or is down).
      LOG.warn("StartSession no se pudo hacer: {}", e.getClass().getSimpleName());
      throw new SessionNotStartedException("StartSession no se pudo hacer", false);
    }
    if (!status.is2xxSuccessful()) {
      throw new SessionNotStartedException("StartSession respondió " + status.value(), false);
    }
  }

  /**
   * EndSession: LIV ends the session with the reason. It answers 204 even when there is nothing to
   * end, so a failure here is a problem of the call, not of the session. It never throws: ending
   * the session of the BFF does not wait for LIV.
   */
  public void endSession(SessionIdentity identity, EndReason reason) {
    try {
      http.post()
          .uri("/internal/v1/sessions/{sid}/end", identity.sessionId())
          .headers(headers -> headers.setBearerAuth(serviceTokens.accessToken()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "issuer", identity.issuer(),
                  "subject", identity.subject(),
                  "reason", reason.name()))
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException e) {
      LOG.warn("EndSession({}) falló: {}", reason, e.getClass().getSimpleName());
    }
  }
}
