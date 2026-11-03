package co.com.sapenlinea.liv.platform;

/**
 * A request refused by a rule, answered with 403 and the code of the criterion (for example a
 * session start of a deactivated user, 001-05). The message is for the log, never the response.
 */
public class RequestRefusedException extends RuntimeException {

  private final String code;

  public RequestRefusedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
