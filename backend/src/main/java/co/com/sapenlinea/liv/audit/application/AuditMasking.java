package co.com.sapenlinea.liv.audit.application;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * What the audit keeps of the personal data of the provider's events (001-18, plan step 9):
 *
 * <ul>
 *   <li>the attempted username as an HMAC-SHA256 with a secret key, plus its length: the same name
 *       always gives the same value, so attempts can be grouped, but it cannot be read back;
 *   <li>the address truncated to /24 in IPv4 and /48 in IPv6;
 *   <li>the author of a change as the HMAC of its id at the provider, an opaque reference.
 * </ul>
 */
public final class AuditMasking {

  private static final int MIN_KEY_LENGTH = 32;
  private static final String ALGORITHM = "HmacSHA256";
  private static final Pattern IPV4 =
      Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

  private final SecretKeySpec key;

  public AuditMasking(String key) {
    if (key == null || key.length() < MIN_KEY_LENGTH) {
      throw new IllegalStateException(
          "The HMAC key of the audit (LIV_AUDIT_HMAC_KEY) needs at least "
              + MIN_KEY_LENGTH
              + " characters");
    }
    this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  /** The HMAC-SHA256 of {@code value}, in hexadecimal. */
  public String hmac(String value) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available", e);
    }
  }

  /**
   * The attempted username as {@code {hmac, length}}, normalized as the provider compares usernames
   * (trimmed, lower case); null if there is none.
   */
  public Map<String, Object> maskUsername(String typed) {
    if (typed == null || typed.isBlank()) {
      return null;
    }
    String normalized = typed.strip().toLowerCase(Locale.ROOT);
    return Map.of("hmac", hmac(normalized), "length", normalized.length());
  }

  /**
   * The network of {@code ip}: /24 for IPv4 and /48 for IPv6, in text; null if it is not an
   * address. It never resolves a name: only literals are parsed.
   */
  public static String truncateIp(String ip) {
    if (ip == null) {
      return null;
    }
    String value = ip.strip();
    Matcher ipv4 = IPV4.matcher(value);
    if (ipv4.matches()) {
      return octets(ipv4)
          ? "%s.%s.%s.0/24".formatted(ipv4.group(1), ipv4.group(2), ipv4.group(3))
          : null;
    }
    if (!IPV6_LITERAL.matcher(value).matches()) {
      return null;
    }
    try {
      // A literal with ':' is parsed, not looked up.
      InetAddress address = InetAddress.getByName(value);
      if (address instanceof Inet4Address mapped) {
        return truncateIp(mapped.getHostAddress());
      }
      return address instanceof Inet6Address ? network48(address.getAddress()) : null;
    } catch (UnknownHostException e) {
      return null;
    }
  }

  /** All four octets are valid, though the fourth is dropped. */
  private static boolean octets(Matcher ipv4) {
    for (int group = 1; group <= 4; group++) {
      if (Integer.parseInt(ipv4.group(group)) > 255) {
        return false;
      }
    }
    return true;
  }

  /** The first 48 bits as an IPv6 network in the compressed form of RFC 5952. */
  private static String network48(byte[] bytes) {
    int[] groups = new int[8];
    for (int i = 0; i < 3; i++) {
      groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
    }
    // Groups 3 to 7 are always zero, so the trailing zeros are the longest run and become "::".
    int end = 2;
    while (end >= 0 && groups[end] == 0) {
      end--;
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i <= end; i++) {
      text.append(i == 0 ? "" : ":").append(Integer.toHexString(groups[i]));
    }
    return text.append("::/48").toString();
  }
}
