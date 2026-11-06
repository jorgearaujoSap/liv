package co.com.sapenlinea.liv.bff.session;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client of the BFF toward the API and the identity provider. It never follows a redirect
 * (a request with a token must not go somewhere else) and it gives up after a timeout, so a service
 * that does not answer does not hold a request thread.
 */
public final class Http {

  private Http() {}

  public static RestClient client(Duration connectTimeout, Duration readTimeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(factory).build();
  }

  /**
   * Whether a refused call will be refused again, whatever is retried: only the answers about the
   * request itself (400, 404, 409, 410, 422). A 401, 403, 407, 408 or 429 can be a credential, a
   * permission or a limit that changes, and is worth trying again.
   */
  static boolean refusedForGood(HttpClientErrorException refused) {
    return switch (refused.getStatusCode().value()) {
      case 400, 404, 409, 410, 422 -> true;
      default -> false;
    };
  }
}
