package co.com.sapenlinea.liv.platform;

import org.springframework.security.core.AuthenticationException;

/** A request that is answered with 401 and the code of the criterion that rejected it. */
public class AuthenticationRejectedException extends AuthenticationException {

  private final String code;

  public AuthenticationRejectedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
