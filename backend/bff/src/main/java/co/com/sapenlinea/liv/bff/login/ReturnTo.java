package co.com.sapenlinea.liv.bff.login;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The route of LIV that a person asked for before signing in (001-01). Only a path of LIV itself
 * comes back after the sign in: the value arrives in a link that anyone can write, so anything else
 * would let a page on the internet send a person who just authenticated to another site (open
 * redirect). A value that is not a safe path becomes the home page.
 */
public final class ReturnTo {

  static final String HOME = "/";

  private static final int MAX_LENGTH = 2048;

  /**
   * URL characters only: no space, control character, backslash, quote, angle bracket or non-ASCII.
   */
  private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9\\-._~!$&'()*+,;=:@/%?#]*");

  /** Routes of the BFF itself are never a destination: they start flows, they are not pages. */
  private static final String[] OWN_ROUTES = {"/bff", "/login/oauth2"};

  private ReturnTo() {}

  public static String sanitize(String requested) {
    if (requested == null
        || requested.length() > MAX_LENGTH
        || !SAFE_PATH.matcher(requested).matches()
        || requested.startsWith("//")
        || hasEncodedSlash(requested)
        || isOwnRoute(requested)) {
      return HOME;
    }
    return requested;
  }

  /** A browser does not decode %2F or %5C in a redirect, but a proxy or a server in between may. */
  private static boolean hasEncodedSlash(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.contains("%2f") || lower.contains("%5c");
  }

  private static boolean isOwnRoute(String path) {
    int end = path.length();
    for (char separator : new char[] {'?', '#'}) {
      int index = path.indexOf(separator);
      if (index >= 0) {
        end = Math.min(end, index);
      }
    }
    String route = path.substring(0, end);
    for (String own : OWN_ROUTES) {
      if (route.equals(own) || route.startsWith(own + "/")) {
        return true;
      }
    }
    return false;
  }
}
