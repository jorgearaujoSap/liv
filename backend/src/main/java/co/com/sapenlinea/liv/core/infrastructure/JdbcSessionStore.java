package co.com.sapenlinea.liv.core.infrastructure;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionEnded;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.core.UserProvisioned;
import co.com.sapenlinea.liv.core.application.SessionStore;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.TokenUser;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Users and sessions with plain SQL under RLS (plan step 8, migrations of T10): every operation
 * opens the administration it works for and then its transaction, so the database filters by it.
 * Times come from the clock of the database.
 */
@Component
class JdbcSessionStore implements SessionStore {

  private final JdbcClient jdbc;
  private final UserDirectory directory;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final TransactionTemplate separateTransaction;

  JdbcSessionStore(
      JdbcClient jdbc,
      UserDirectory directory,
      ApplicationEventPublisher events,
      PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.directory = directory;
    this.events = events;
    this.transaction = new TransactionTemplate(transactions);
    this.separateTransaction = new TransactionTemplate(transactions);
    separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public Optional<StartedSession> start(DirectoryUser user, TokenUser token, String sid) {
    return inAdministration(
        user.administrationId(),
        () -> {
          requireActiveAdministration(user.administrationId());
          return startSession(user, token, sid);
        });
  }

  @Override
  public Optional<StartedSession> provisionAndStart(
      UUID administrationId, String fullName, String email, TokenUser token, String sid) {
    return inAdministration(
        administrationId,
        () -> {
          requireActiveAdministration(administrationId);
          // A concurrent first login waits on the unique key and then inserts nothing.
          Optional<UUID> created =
              jdbc.sql(
                      """
                      INSERT INTO core.admin_user (administration_id, full_name, email, status,
                                                   identity_issuer, identity_subject)
                      VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                      ON CONFLICT ON CONSTRAINT admin_user_identity_key DO NOTHING
                      RETURNING id
                      """)
                  .params(administrationId, fullName, email, token.issuer(), token.subject())
                  .query(UUID.class)
                  .optional();
          if (created.isPresent()) {
            DirectoryUser user = new DirectoryUser(created.get(), administrationId, true);
            events.publishEvent(new UserProvisioned(user.id(), administrationId));
            return startSession(user, token, sid);
          }
          DirectoryUser existing =
              directory
                  .resolve(token.issuer(), token.subject())
                  .orElseThrow(() -> new IllegalStateException("User vanished after a conflict"));
          if (!existing.active() || !existing.administrationId().equals(administrationId)) {
            throw new UserRegisteredElsewhereException(existing);
          }
          return startSession(existing, token, sid);
        });
  }

  @Override
  public boolean end(DirectoryUser user, String issuer, String subject, String sid, String reason) {
    return inAdministration(
        user.administrationId(),
        () -> {
          Optional<OffsetDateTime> endedAt =
              jdbc.sql(
                      """
                      UPDATE core.user_session SET ended_at = now(), end_reason = ?
                       WHERE sid = ? AND user_id = ? AND identity_issuer = ? AND identity_subject = ?
                         AND ended_at IS NULL
                      RETURNING ended_at
                      """)
                  .params(reason, sid, user.id(), issuer, subject)
                  .query(OffsetDateTime.class)
                  .optional();
          endedAt.ifPresent(at ->
              events.publishEvent(
                  new SessionEnded(
                      sid, user.id(), user.administrationId(), reason, at.toInstant())));
          return endedAt.isPresent();
        });
  }

  @Override
  public void denied(String code, TokenUser token, UUID userId, UUID administrationId) {
    separateTransaction.executeWithoutResult(status ->
        events.publishEvent(
            new SessionDenied(code, token.issuer(), token.subject(), userId, administrationId)));
  }

  /** The session row; a retry of the same user returns it, another user's sid returns empty. */
  private Optional<StartedSession> startSession(DirectoryUser user, TokenUser token, String sid) {
    Optional<OffsetDateTime> startedAt =
        jdbc.sql(
                """
                INSERT INTO core.user_session (sid, user_id, administration_id, identity_issuer,
                                               identity_subject, started_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (sid) DO NOTHING
                RETURNING started_at
                """)
            .params(sid, user.id(), user.administrationId(), token.issuer(), token.subject())
            .query(OffsetDateTime.class)
            .optional();
    if (startedAt.isEmpty()) {
      // Under RLS a session of another administration is not even visible: it is not this one.
      return jdbc.sql("SELECT user_id FROM core.user_session WHERE sid = ?")
          .param(sid)
          .query(UUID.class)
          .optional()
          .filter(user.id()::equals)
          .map(id -> new StartedSession(id, false));
    }
    jdbc.sql("UPDATE core.admin_user SET last_login_at = now() WHERE id = ?")
        .param(user.id())
        .update();
    events.publishEvent(
        new SessionStarted(sid, user.id(), user.administrationId(), startedAt.get().toInstant()));
    return Optional.of(new StartedSession(user.id(), true));
  }

  private void requireActiveAdministration(UUID administrationId) {
    boolean active =
        jdbc.sql("SELECT active FROM core.tax_administration WHERE id = ?")
            .param(administrationId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (!active) {
      throw new AdministrationUnavailableException(administrationId);
    }
  }

  private <T> T inAdministration(UUID administrationId, Supplier<T> work) {
    try (var scope = AdministrationContext.open(administrationId)) {
      return transaction.execute(status -> work.get());
    }
  }
}
