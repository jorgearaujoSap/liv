package co.com.sapenlinea.liv.bff.login;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

  private static final Pattern ENCODED_SLASH = Pattern.compile("%2[fF]");
  private static final Pattern ENCODED_BACKSLASH = Pattern.compile("%5[cC]");

  /** Routes of the BFF itself are never a destination: they start flows, they are not pages. */
  private static final String[] OWN_ROUTES = {"/bff", "/login/oauth2"};

  private ReturnTo() {}

  /**
   * The path to go back to, or the home page. A {@code %2F} or {@code %5C} in the route (decision
   * of the responsible person in T21, done in T19) is decoded first and the path is checked as
   * decoded: a browser does not decode them in a redirect, but a proxy or a server in between may,
   * so what is checked is what any of them would see. The path comes back decoded.
   */
  public static String sanitize(String requested) {
    if (requested == null || requested.length() > MAX_LENGTH) {
      return HOME;
    }
    String path = withSlashesDecoded(requested);
    if (!SAFE_PATH.matcher(path).matches() || path.startsWith("//") || leadsToAnOwnRoute(path)) {
      return HOME;
    }
    return path;
  }

  /**
   * The route with {@code %2F} as {@code /} and {@code %5C} as {@code \}; the query and the
   * fragment as they are, where an encoded slash is only data. A backslash is not a safe character,
   * so a decoded {@code %5C} is refused.
   */
  private static String withSlashesDecoded(String value) {
    int end = value.length();
    for (char separator : new char[] {'?', '#'}) {
      int at = value.indexOf(separator);
      if (at >= 0 && at < end) {
        end = at;
      }
    }
    String route = ENCODED_SLASH.matcher(value.substring(0, end)).replaceAll("/");
    route = ENCODED_BACKSLASH.matcher(route).replaceAll("\\\\");
    return route + value.substring(end);
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
    if (decoded.contains(";")) {
      // Path parameters (/x/..;/y): a server drops them and normalizes what is left.
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
