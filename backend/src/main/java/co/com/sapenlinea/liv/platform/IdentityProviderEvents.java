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

  /** Up to {@code limit} events of users (failed logins, lockouts) from {@code from} on. */
  EventsPage userEvents(Instant from, int limit);

  /** Up to {@code limit} changes of administrators to users, from {@code from} on. */
  EventsPage adminEvents(Instant from, int limit);

  /**
   * Events in the order they happened, oldest first.
   *
   * @param events the events read
   * @param more whether the provider has more after the last one: the next read must not skip them
   */
  record EventsPage(List<ProviderEvent> events, boolean more) {

    public EventsPage {
      events = List.copyOf(events);
    }
  }

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
      String authorSubject) {}

  /** Who originated the event. */
  enum Kind {
    USER,
    ADMIN
  }
}
