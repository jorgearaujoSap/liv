package co.com.sapenlinea.liv.audit.infrastructure;

import co.com.sapenlinea.liv.audit.application.AuditLog;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code audit.audit_event} and {@code audit.import_cursor} under RLS (migration V202611010915):
 *
 * <ul>
 *   <li>a row of an administration is written with that administration as the context of its own
 *       transaction, opened here: the listeners and the importer run outside any request;
 *   <li>a platform row of the importer is written in a transaction marked as a platform job, with
 *       the mark local to that transaction;
 *   <li>a refused LIV start without a valid administration goes through {@code
 *       audit.record_platform_session_denied}.
 * </ul>
 *
 * <p>Every insert is {@code ON CONFLICT DO NOTHING} without a target and without {@code RETURNING},
 * with the id made here: a platform row has no read policy, and a target or {@code RETURNING} would
 * need one (migration comment).
 */
@Component
class JdbcAuditLog implements AuditLog {

  private static final String IDENTITY_PROVIDER = "IDENTITY_PROVIDER";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;

  JdbcAuditLog(JdbcClient jdbc, PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactions);
  }

  @Override
  public void append(Entry entry) {
    if (entry.administrationId() != null) {
      inAdministration(entry.administrationId(), () -> insert(entry));
      return;
    }
    if (!IDENTITY_PROVIDER.equals(entry.source())) {
      throw new IllegalArgumentException(
          "Only the importer of the identity provider writes platform rows (ADR 0025)");
    }
    runInPlatformJob(() -> insert(entry));
  }

  @Override
  public void appendSessionDenied(Entry entry) {
    if (entry.administrationId() == null) {
      ownTransaction(() -> recordPlatformDenied(entry));
      return;
    }
    // A claimed administration may not exist: then the row cannot belong to it.
    inAdministration(
        entry.administrationId(),
        () -> {
          if (administrationExists(entry.administrationId())) {
            insert(entry);
          } else {
            recordPlatformDenied(entry);
          }
        });
  }

  @Override
  public Optional<Cursor> cursor() {
    return inPlatformJob(() ->
        jdbc.sql(
                "SELECT last_event_at, seen_event_ids, complete FROM audit.import_cursor"
                    + " WHERE source = 'IDENTITY_PROVIDER'")
            .query((row, number) ->
                new Cursor(
                    row.getTimestamp("last_event_at").toInstant(),
                    ids(row.getArray("seen_event_ids")),
                    row.getBoolean("complete")))
            .optional());
  }

  @Override
  public void saveCursor(Cursor cursor) {
    runInPlatformJob(() ->
        jdbc.sql(
                """
                INSERT INTO audit.import_cursor (source, last_event_at, seen_event_ids, complete)
                VALUES ('IDENTITY_PROVIDER', ?, ?, ?)
                ON CONFLICT (source) DO UPDATE
                  SET last_event_at = EXCLUDED.last_event_at,
                      seen_event_ids = EXCLUDED.seen_event_ids,
                      complete = EXCLUDED.complete,
                      updated_at = now()
                  WHERE audit.import_cursor.last_event_at <= EXCLUDED.last_event_at
                """)
            .params(
                Timestamp.from(cursor.lastEventAt()),
                cursor.seenEventIds().toArray(String[]::new),
                cursor.complete())
            .update());
  }

  private void insert(Entry entry) {
    jdbc.sql(
            """
            INSERT INTO audit.audit_event (id, user_id, administration_id, entity_name, entity_id,
                                           operation, new_values, occurred_at, correlation_id,
                                           source, source_event_id, actor_ref)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """)
        .params(
            UUID.randomUUID(),
            entry.userId(),
            entry.administrationId(),
            entry.entityName(),
            entry.entityId(),
            entry.operation(),
            JSON.writeValueAsString(entry.newValues()),
            Timestamp.from(entry.occurredAt()),
            entry.correlationId(),
            entry.source(),
            entry.sourceEventId(),
            entry.actorRef())
        .update();
  }

  private void recordPlatformDenied(Entry entry) {
    jdbc.sql("SELECT audit.record_platform_session_denied(?, ?, ?, CAST(? AS jsonb), ?)")
        .params(
            entry.sourceEventId(),
            Timestamp.from(entry.occurredAt()),
            entry.correlationId(),
            JSON.writeValueAsString(entry.newValues()),
            entry.actorRef())
        .query((row, number) -> number)
        .list();
  }

  /** Under RLS the administration of the context is the only one visible. */
  private boolean administrationExists(UUID administrationId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.tax_administration WHERE id = ?)")
        .param(administrationId)
        .query(Boolean.class)
        .single();
  }

  private void inAdministration(UUID administrationId, Runnable work) {
    try (var scope = AdministrationContext.open(administrationId)) {
      ownTransaction(work);
    }
  }

  /**
   * A transaction marked as a platform job (doc. 02 §5.1): the mark is local to it, so it never
   * stays on a pooled connection.
   */
  private <T> T inPlatformJob(Supplier<T> work) {
    requireNoTransaction();
    return transaction.execute(status -> {
      jdbc.sql("SELECT set_config('app.platform_job', 'on', true)")
          .query((row, number) -> number)
          .list();
      return work.get();
    });
  }

  /** A transaction of its own: the audit is never written inside another one. */
  private void ownTransaction(Runnable work) {
    requireNoTransaction();
    transaction.executeWithoutResult(status -> work.run());
  }

  /**
   * The audit runs from listeners and jobs, outside any transaction. Inside one it would join it:
   * the mark of a platform job would reach the rest of that transaction, and a row would roll back
   * with it.
   */
  private static void requireNoTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("The audit log runs outside any transaction");
    }
  }

  private void runInPlatformJob(Runnable work) {
    inPlatformJob(() -> {
      work.run();
      return null;
    });
  }

  private static Set<String> ids(Array array) throws SQLException {
    return array == null ? Set.of() : Set.copyOf(Arrays.asList((String[]) array.getArray()));
  }
}
