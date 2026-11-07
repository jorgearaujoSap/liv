package co.com.sapenlinea.liv.bff.login;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Whether a request is the person navigating, as opposed to an image, a script, a {@code fetch} or
 * a preload that the browser makes on its own ({@code Sec-Purpose}). Browsers say what a request is
 * in {@code Sec-Fetch-*}. A client that sends none is not a browser page, so it is treated as a
 * navigation: the {@code SameSite=Strict} cookie already keeps other sites from using it.
 */
public final class Navigations {

  private Navigations() {}

  public static boolean isANavigation(HttpServletRequest request) {
    String destination = request.getHeader("Sec-Fetch-Dest");
    String mode = request.getHeader("Sec-Fetch-Mode");
    return request.getHeader("Sec-Purpose") == null
        && (destination == null || destination.equals("document"))
        && (mode == null || mode.equals("navigate"));
  }
}
