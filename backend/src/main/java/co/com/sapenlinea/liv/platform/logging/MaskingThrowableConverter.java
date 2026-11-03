package co.com.sapenlinea.liv.platform.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Spring Boot's exception trace with the credentials masked as in {@link MaskingMessageConverter}:
 * an exception message may carry a token. {@code logging.exception-conversion-word} selects it.
 */
public class MaskingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

  @Override
  protected String throwableProxyToString(IThrowableProxy throwable) {
    return MaskingMessageConverter.mask(super.throwableProxyToString(throwable));
  }
}
