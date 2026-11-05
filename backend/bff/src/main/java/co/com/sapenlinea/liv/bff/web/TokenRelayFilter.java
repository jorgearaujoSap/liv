package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.TokenRenewal;
import co.com.sapenlinea.liv.bff.session.TokenRenewalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The token relay of the gateway: the request goes to the API with the access token of the session
 * as {@code Authorization: Bearer}, renewed first if less than a minute is left. Spring Cloud
 * Gateway's own {@code TokenRelay} only works with an OAuth2 authentication, and the session here
 * holds none (no token in the security context: {@link SessionAuthentication}). A request is never
 * forwarded without a valid token: if the provider refuses the renewal for good ({@code
 * invalid_grant}) the session ends with {@code PROVIDER} and the answer is 401; if it does not
 * answer, the request goes on with the token the session still has, and if that one is over too,
 * the answer is 502 and the session stays.
 */
final class TokenRelayFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  private final TokenRenewal renewal;
  private final SessionCloser closer;

  TokenRelayFilter(TokenRenewal renewal, SessionCloser closer) {
    this.renewal = renewal;
    this.closer = closer;
  }

  @Override
  public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
      throws Exception {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof SessionAuthentication person)) {
      return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
    }
    HttpServletRequest servletRequest = request.servletRequest();
    HttpServletResponse servletResponse =
        ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    String accessToken;
    try {
      accessToken = renewal.accessToken(servletRequest, servletResponse, person);
    } catch (TokenRenewalException e) {
      if (!e.sessionIsOver()) {
        return ServerResponse.status(HttpStatus.BAD_GATEWAY).build();
      }
      closer.closeAndDelete(servletRequest, person, EndReason.PROVIDER);
      return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
    }
    return next.handle(
        ServerRequest.from(request).headers(headers -> headers.setBearerAuth(accessToken)).build());
  }
}
