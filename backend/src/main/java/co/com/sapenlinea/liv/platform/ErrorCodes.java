package co.com.sapenlinea.liv.platform;

/**
 * Codes of the {@code code} field of Problem Details (docs/arquitectura/01 §10): the acceptance
 * criterion or rule that rejected the request.
 */
public final class ErrorCodes {

  /** No valid token, or no LIV session for it (001-14). */
  public static final String NOT_AUTHENTICATED = "001-14";

  /** The registered user is deactivated (001-05). */
  public static final String USER_INACTIVE = "001-05";

  /** The administration of the token is not the registered one (001-17). */
  public static final String ADMINISTRATION_MISMATCH = "001-17";

  /** The user lacks the permission (001-16). */
  public static final String FORBIDDEN = "001-16";

  private ErrorCodes() {}
}
