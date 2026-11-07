package co.com.sapenlinea.liv.bff.login;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Whether a request is the person navigating, as opposed to an image, a script, a {@code fetch} or
 * a preload that the browser makes on its own ({@code Sec-Purpose}). Browsers say what a request is
 * in {@code Sec-Fetch-*}; a client that does not send them is not a browser page and counts as a
 * navigation (the cookie is {@code SameSite=Strict} anyway).
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
