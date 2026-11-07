package co.com.sapenlinea.liv.bff.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A {@code GET /bff/login} that is not a navigation (001-01). The sign in only starts, and only
 * ends an open session, when the person navigates here ({@link Navigations}). Anything else gets
 * here: most often the browser preloading the address from its history, which it shows as is when
 * the person opens it.
 *
 * <p>So the answer is a page that does nothing while it is preloaded and, once the person opens it,
 * asks for the same address again, now as a navigation: the sign in starts, and an open session is
 * ended first, as for anybody who navigates here. A 404 here was shown instead of the sign in; a
 * redirect to another route of LIV would have shown the open session of whoever was signed in
 * before. Without scripts, the page has a link that does the same.
 *
 * <p>The page starts and ends nothing by itself, and its only script is the one allowed by hash in
 * its {@code Content-Security-Policy}.
 */
@RestController
final class LoginPreloadController {

  /** While preloaded, wait until the person opens the page; then ask for it again. */
  static final String SCRIPT =
      "if(document.prerendering){document.addEventListener('prerenderingchange',"
          + "function(){location.replace(location.href)},{once:true})}"
          + "else{location.replace(location.href)}";

  static final String CSP =
      "default-src 'none'; script-src '"
          + sha256(SCRIPT)
          + "'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

  @GetMapping(LoginAuthorizationRequestResolver.LOGIN_PATH)
  void preload(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // The same value the sign in reads (the first one), checked: the link is always a route of LIV.
    String returnTo = ReturnTo.sanitize(request.getParameter("returnTo"));
    String again =
        LoginAuthorizationRequestResolver.LOGIN_PATH
            + "?returnTo="
            + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("text/html;charset=UTF-8");
    response
        .getWriter()
        .write(
            "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<title>LIV</title><script>"
                + SCRIPT
                + "</script></head><body><p><a href=\""
                + again
                + "\">Continuar al inicio de sesión</a></p></body></html>");
  }

  private static String sha256(String script) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
      return "sha256-" + Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
    }
  }
}
