package co.com.sapenlinea.liv.bff.login;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A {@code GET /bff/login} that is not a navigation (001-01): {@link
 * LoginAuthorizationRequestResolver} only starts a sign in when the person navigates to it, so this
 * request reaches here. Most often it is the browser preloading the address while it is typed
 * ({@code Sec-Purpose: prefetch;prerender}); when the person opens the page, the browser shows what
 * it preloaded.
 *
 * <p>The answer goes to the route of LIV in {@code returnTo}, checked by {@link ReturnTo}, and
 * touches no session: nothing starts and nothing ends. The application on that route waits until
 * the person opens it, and then, without a session, navigates to {@code /bff/login} for real.
 * Before, the answer was a 404, and the browser showed that 404 instead of the sign in.
 */
@RestController
final class LoginPreloadController {

  @GetMapping(LoginAuthorizationRequestResolver.LOGIN_PATH)
  void preload(
      @RequestParam(name = "returnTo", required = false) String returnTo,
      HttpServletResponse response) {
    LoginRejection.redirect(response, ReturnTo.sanitize(returnTo));
  }
}
