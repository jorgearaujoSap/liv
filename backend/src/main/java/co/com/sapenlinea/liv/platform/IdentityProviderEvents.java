package co.com.sapenlinea.liv.platform;

import java.time.Instant;
import java.util.List;

/**
 * The events of the identity provider that LIV copies to its audit (001-18, plan step 9): failed
 * logins, lockouts and code exchange errors, and the changes an administrator makes to a user
 * (roles, attributes and state). Successful logins and logouts are not here: LIV records them
 * itself. Only the adapter of the provider knows its API (doc. 04 §3.1); the events come in this
 * neutral form, with the fields of a whitelist.
 */
public interface IdentityProviderEvents {

  /** Events of users (failed logins, lockouts), from {@code from} on, oldest first. */
  List<ProviderEvent> userEvents(Instant from, int max);

  /** Events of administrators on users (roles, attributes, state), oldest first. */
  List<ProviderEvent> adminEvents(Instant from, int max);

  /**
   * An event of the provider.
   *
   * @param id the provider's id of the event, unique in the provider
   * @param kind a user event or an administrator's change
   * @param type what happened, for example {@code LOGIN_ERROR} or {@code REALM_ROLE_MAPPING_CREATE}
   * @param time when it happened
   * @param subject the provider's id of the user the event is about ({@code sub}), if known
   * @param attemptedUsername the username typed in a failed login, in clear: the audit masks it
   * @param ipAddress the address of the client, in full: the audit truncates it
   * @param error the provider's error code of a failed event, for example {@code
   *     invalid_user_credentials}
   * @param authorSubject the provider's id of the administrator who made a change
   * @param roles the names of the roles granted or removed by a change of roles
   * @param enabled the state of the user after a change of the user, if the change says it
   */
  record ProviderEvent(
      String id,
      Kind kind,
      String type,
      Instant time,
      String subject,
      String attemptedUsername,
      String ipAddress,
      String error,
      String authorSubject,
      List<String> roles,
      Boolean enabled) {

    public ProviderEvent {
      roles = roles == null ? List.of() : List.copyOf(roles);
    }
  }

  /** Who originated the event. */
  enum Kind {
    USER,
    ADMIN
  }
}
