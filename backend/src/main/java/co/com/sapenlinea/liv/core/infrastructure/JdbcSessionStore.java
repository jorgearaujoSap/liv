package co.com.sapenlinea.liv.core.infrastructure;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionEnded;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.core.UserProvisioned;
import co.com.sapenlinea.liv.core.application.SessionStore;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.Correlation;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.TokenUser;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.time.Clock;
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
 * Times are {@code now()}, the start of the transaction by the clock of the database.
 */
@Component
class JdbcSessionStore implements SessionStore {

  private final JdbcClient jdbc;
  private final UserDirectory directory;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate administrationTransaction;
  private final TransactionTemplate newTransaction;
  private final Clock clock;

  JdbcSessionStore(
      JdbcClient jdbc,
      UserDirectory directory,
      ApplicationEventPublisher events,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.jdbc = jdbc;
    this.directory = directory;
    this.events = events;
    this.clock = clock;
    this.administrationTransaction = new TransactionTemplate(transactions);
    this.newTransaction = new TransactionTemplate(transactions);
    newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public StartOutcome start(DirectoryUser user, TokenUser token, String sid) {
    return startIn(user.administrationId(), () -> startSession(user, token, sid));
  }

  @Override
  public StartOutcome provisionAndStart(
      UUID administrationId, String fullName, String email, TokenUser token, String sid) {
    return startIn(
        administrationId,
        () -> {
          Optional<UUID> created = insertUser(administrationId, fullName, email, token);
          if (created.isPresent()) {
            events.publishEvent(
                new UserProvisioned(
                    created.get(),
                    administrationId,
                    clock.instant(),
                    UUID.randomUUID(),
                    Correlation.current().orElse(null)));
            return startSession(
                new DirectoryUser(created.get(), administrationId, true), token, sid);
          }
          // Another login inserted it first; its commit released our insert. The function reads
          // it whatever the RLS context (READ COMMITTED: a new snapshot per statement).
          DirectoryUser existing =
              directory
                  .resolve(token.issuer(), token.subject())
                  .orElseThrow(() -> new IllegalStateException("User vanished after a conflict"));
          return startSession(existing, token, sid);
        });
  }

  @Override
  public boolean end(DirectoryUser user, String issuer, String subject, String sid, String reason) {
    try (var scope = AdministrationContext.open(user.administrationId())) {
      return Boolean.TRUE.equals(
          administrationTransaction.execute(status -> {
            Optional<OffsetDateTime> endedAt =
                jdbc.sql(
                        """
                        UPDATE core.user_session SET ended_at = now(), end_reason = ?
                         WHERE sid = ? AND user_id = ? AND identity_issuer = ?
                           AND identity_subject = ? AND ended_at IS NULL
                        RETURNING ended_at
                        """)
                    .params(reason, sid, user.id(), issuer, subject)
                    .query(OffsetDateTime.class)
                    .optional();
            endedAt.ifPresent(at ->
                events.publishEvent(
                    new SessionEnded(
                        sid,
                        user.id(),
                        user.administrationId(),
                        reason,
                        at.toInstant(),
                        UUID.randomUUID(),
                        Correlation.current().orElse(null))));
            return endedAt.isPresent();
          }));
    }
  }

  /**
   * The audit listener of T15 will run after this transaction commits, without an administration
   * context: the refusal may belong to no administration (ADR 0025, platform rows).
   */
  @Override
  public void denied(SessionDenied refusal) {
    newTransaction.executeWithoutResult(status -> events.publishEvent(refusal));
  }

  /**
   * One transaction in the administration: it checks that the administration is active, runs the
   * start and keeps its changes only if a new session was started.
   */
  private StartOutcome startIn(UUID administrationId, Supplier<StartOutcome> start) {
    try (var scope = AdministrationContext.open(administrationId)) {
      return administrationTransaction.execute(status -> {
        StartOutcome outcome =
            activeAdministration(administrationId)
                ? start.get()
                : new StartOutcome(Result.ADMINISTRATION_UNAVAILABLE, null);
        if (outcome.result() != Result.STARTED) {
          status.setRollbackOnly();
        }
        return outcome;
      });
    }
  }

  private Optional<UUID> insertUser(
      UUID administrationId, String fullName, String email, TokenUser token) {
    // A concurrent first login waits on the unique key and then inserts nothing.
    return jdbc.sql(
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
  }

  /**
   * Checks the user again inside the transaction (it may have been deactivated or moved since it
   * was read), then records the session; a retry, another user's sid or an ended session are told
   * apart (001-14).
   */
  private StartOutcome startSession(DirectoryUser user, TokenUser token, String sid) {
    int stillActive =
        jdbc.sql(
                """
                UPDATE core.admin_user SET last_login_at = now()
                 WHERE id = ? AND administration_id = ? AND status = 'ACTIVE'
                """)
            .params(user.id(), user.administrationId())
            .update();
    if (stillActive == 0) {
      return new StartOutcome(Result.USER_CHANGED, user.id());
    }
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
    if (startedAt.isPresent()) {
      events.publishEvent(
          new SessionStarted(
              sid,
              user.id(),
              user.administrationId(),
              startedAt.get().toInstant(),
              UUID.randomUUID(),
              Correlation.current().orElse(null)));
      return new StartOutcome(Result.STARTED, user.id());
    }
    // Under RLS a session of another administration is not even visible: it is not this user's.
    return jdbc.sql("SELECT ended_at IS NULL FROM core.user_session WHERE sid = ? AND user_id = ?")
        .params(sid, user.id())
        .query(Boolean.class)
        .optional()
        .map(open -> new StartOutcome(open ? Result.RETRIED : Result.SESSION_CLOSED, user.id()))
        .orElse(new StartOutcome(Result.SID_TAKEN, null));
  }

  private boolean activeAdministration(UUID administrationId) {
    return jdbc.sql("SELECT active FROM core.tax_administration WHERE id = ?")
        .param(administrationId)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }
}
