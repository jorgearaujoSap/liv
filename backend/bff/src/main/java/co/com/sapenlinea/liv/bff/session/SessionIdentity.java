package co.com.sapenlinea.liv.bff.session;

import java.io.Serializable;

/**
 * Who the session belongs to, in the terms the API uses to end it: the issuer and the {@code sub}
 * of the identity provider and the {@code sid} of its session (EndSession, core-internal.yaml). It
 * is all the security context keeps: no token, not even the id token (001-11).
 */
public record SessionIdentity(String issuer, String subject, String sessionId)
    implements Serializable {}
