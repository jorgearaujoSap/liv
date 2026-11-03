package co.com.sapenlinea.liv.core.application;

import java.util.Optional;
import java.util.UUID;

/** The registry data of a user and its administration, read under RLS (001-13, 001-17). */
public interface UserProfiles {

  /**
   * The user in its registered administration; empty if it is no longer there.
   *
   * @param userId the LIV id of the user
   * @param administrationId its registered administration, the one the RLS context has
   */
  Optional<Profile> find(UUID userId, UUID administrationId);

  /**
   * @param fullName the name in the registry
   * @param email the email in the registry
   * @param administrationName the name of the administration
   */
  record Profile(String fullName, String email, String administrationName) {}
}
