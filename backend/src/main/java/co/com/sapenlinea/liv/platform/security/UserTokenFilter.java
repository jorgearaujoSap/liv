package co.com.sapenlinea.liv.platform.security;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.VerifiedUserToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On session start, after the service token is validated, validates the user token of {@code
 * X-User-Token} with the decoder of {@code /api/**} (core-internal.yaml). A missing or invalid
 * token is a 401 with 001-14 before the controller runs; a valid one is left in {@link
 * VerifiedUserToken}.
 */
final class UserTokenFilter extends OncePerRequestFilter {

  static final String HEADER = "X-User-Token";

  private final RequestMatcher sessionStart;
  private final JwtDecoder userTokens;

  UserTokenFilter(RequestMatcher sessionStart, JwtDecoder userTokens) {
    this.sessionStart = sessionStart;
    this.userTokens = userTokens;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !sessionStart.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = request.getHeader(HEADER);
    if (!StringUtils.hasText(token)) {
      throw new AuthenticationRejectedException(ErrorCodes.NOT_AUTHENTICATED, "No user token");
    }
    try {
      request.setAttribute(VerifiedUserToken.ATTRIBUTE, userTokens.decode(token));
    } catch (JwtException e) {
      throw new AuthenticationRejectedException(ErrorCodes.NOT_AUTHENTICATED, "Invalid user token");
    }
    chain.doFilter(request, response);
  }
}
