package co.com.sapenlinea.liv.platform.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Masks credentials in every log message (doc. 02 §6, "filtro de enmascaramiento obligatorio"): any
 * JWT, the value after {@code Bearer}, and the value of headers and parameters that carry
 * credentials ({@code X-User-Token}, {@code Authorization}, {@code client_secret} and OAuth
 * tokens), with or without quotes. It replaces the {@code %m} conversion word in {@code
 * logback-spring.xml}; {@link MaskingThrowableConverter} does the same for exception traces.
 */
public class MaskingMessageConverter extends MessageConverter {

  static final String MASK = "[oculto]";

  /** A JWT starts with {@code eyJ} ({@code {"} in base64url); the signature part is optional. */
  private static final Pattern JWT = Pattern.compile("eyJ[\\w-]*\\.[\\w-]*(?:\\.[\\w-]*)?");

  /** {@code X-User-Token: "abc"} → {@code X-User-Token: "[oculto]"}; keeps the name (group 1). */
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "(?i)((?:x-user-token|authorization|client_secret|access_token|refresh_token|id_token)"
              + "[\"']?\\s*[:=]\\s*[\"']?)(?:bearer\\s+)?[^\\s,;\"'}]+");

  /** {@code Bearer abc} → {@code Bearer [oculto]}. */
  private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[^\\s,;\"'}]+");

  @Override
  public String convert(ILoggingEvent event) {
    return mask(super.convert(event));
  }

  static String mask(String text) {
    if (text == null) {
      return null;
    }
    String masked = JWT.matcher(text).replaceAll(MASK);
    masked = CREDENTIAL.matcher(masked).replaceAll("$1" + MASK);
    return BEARER.matcher(masked).replaceAll("$1" + MASK);
  }
}
