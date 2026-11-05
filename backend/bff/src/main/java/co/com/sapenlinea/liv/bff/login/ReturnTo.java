package co.com.sapenlinea.liv.bff.login;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
        || leadsToAnOwnRoute(requested)) {
      return HOME;
    }
    return requested;
  }

  /** A browser does not decode %2F or %5C in a redirect, but a proxy or a server in between may. */
  private static boolean hasEncodedSlash(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.contains("%2f") || lower.contains("%5c");
  }

  /**
   * Whether the path, once the browser and the server normalize it, is a route of the BFF: letters
   * written as {@code %XX} (a server decodes them) and the segments {@code .} and {@code ..} (a
   * browser resolves them) must not hide it. What cannot be decoded is refused.
   */
  private static boolean leadsToAnOwnRoute(String path) {
    String route = path.split("[?#]", 2)[0];
    String decoded;
    try {
      decoded = URLDecoder.decode(route.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return true;
    }
    for (String segment : decoded.split("/")) {
      if (segment.equals(".") || segment.equals("..")) {
        return true;
      }
    }
    for (String own : OWN_ROUTES) {
      if (decoded.equals(own) || decoded.startsWith(own + "/")) {
        return true;
      }
    }
    return false;
  }
}
