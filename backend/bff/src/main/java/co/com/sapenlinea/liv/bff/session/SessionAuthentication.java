package co.com.sapenlinea.liv.bff.session;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The authentication of a person with an open session. It replaces the OIDC authentication once LIV
 * started the session, so the security context stored in the database holds no token. The name is
 * the {@code sub}: Spring Session indexes the session by it (the column takes up to 255 characters,
 * the most OIDC allows).
 */
public final class SessionAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final SessionIdentity identity;

  public SessionAuthentication(SessionIdentity identity) {
    super(List.of());
    this.identity = identity;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public SessionIdentity getPrincipal() {
    return identity;
  }

  @Override
  public String getName() {
    return identity.subject();
  }
}
