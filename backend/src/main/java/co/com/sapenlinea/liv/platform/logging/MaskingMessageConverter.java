package co.com.sapenlinea.liv.platform.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Masks credentials in every log message (doc. 02 §6, "filtro de enmascaramiento obligatorio"): any
 * JWT, and the value of {@code X-User-Token} even when it is not a JWT. It replaces the {@code %m}
 * conversion word in {@code logback-spring.xml}, so it applies to all appenders. It does not see
 * exception traces or the MDC: no LIV code puts a token there.
 */
public class MaskingMessageConverter extends MessageConverter {

  static final String MASK = "[oculto]";

  /** A JWT starts with {@code eyJ} ({@code {"} in base64url); the signature part is optional. */
  private static final Pattern JWT = Pattern.compile("eyJ[\\w-]*\\.[\\w-]*(?:\\.[\\w-]*)?");

  private static final Pattern USER_TOKEN_HEADER =
      Pattern.compile("(?i)(x-user-token[\"']?\\s*[:=]\\s*)[^\\s,;\"']+");

  @Override
  public String convert(ILoggingEvent event) {
    return mask(super.convert(event));
  }

  static String mask(String message) {
    if (message == null) {
      return null;
    }
    String masked = JWT.matcher(message).replaceAll(MASK);
    return USER_TOKEN_HEADER.matcher(masked).replaceAll("$1" + MASK);
  }
}
