package co.com.sapenlinea.liv.platform;

import java.util.Optional;

/**
 * Port to the LIV user registry (ADR 0025): {@code platform} asks, {@code core} answers. Resolves
 * the identity of a token (issuer and subject) to the registered user, without a tenant context.
 */
public interface UserDirectory {

  Optional<DirectoryUser> resolve(String issuer, String subject);
}
