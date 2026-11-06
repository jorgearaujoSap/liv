package co.com.sapenlinea.liv.bff.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.DelegatingAccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * The anti-CSRF refusal (001-12), as {@code bff.yaml} declares it ({@code CsrfRejected}): a 403 in
 * {@code application/problem+json} with code 001-12, in {@code /bff/**} and in {@code /api/**}. The
 * frontend tells it apart from any other 403 by that code, so only a {@link CsrfException} gets it:
 * any other denial of the BFF answers a bare 403.
 */
public final class CsrfRejection {

  /** The code of the refusal, the one the contract shows as its example. */
  public static final String CODE = "001-12";

  private CsrfRejection() {}

  /** The handler of every denial of the chain: the refusal for CSRF, a bare 403 for the rest. */
  public static AccessDeniedHandler handler() {
    AccessDeniedHandler csrf =
        (request, response, exception) -> {
          response.setStatus(HttpStatus.FORBIDDEN.value());
          response.setContentType("application/problem+json");
          response.setCharacterEncoding("UTF-8");
          response.getWriter().write("{\"status\":403,\"code\":\"" + CODE + "\"}");
        };
    LinkedHashMap<Class<? extends AccessDeniedException>, AccessDeniedHandler> handlers =
        new LinkedHashMap<>(Map.of(CsrfException.class, csrf));
    return new DelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
  }
}
