package co.com.sapenlinea.liv.bff.session;

/**
 * The two OIDC clients of the BFF (plan, step 3): {@code liv-bff} signs people in with the
 * authorization code; {@code liv-bff-service} is only for client credentials, to call the internal
 * endpoints of the API.
 */
public final class OidcRegistrations {

  /** Registration id of the sign in. It is also the last segment of the redirect URI. */
  public static final String USER = "liv";

  public static final String SERVICE = "liv-bff-service";

  private OidcRegistrations() {}
}
