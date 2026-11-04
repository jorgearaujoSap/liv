package co.com.sapenlinea.liv.audit;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code audit.audit_event} as its owner, which also sees the platform rows that {@code
 * liv_app} cannot read. The audit is written after the commit, by an asynchronous listener: the
 * readers wait for the rows instead of reading once.
 */
final class AuditRows {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String COLUMNS =
      "administration_id, user_id, entity_name, entity_id, operation, new_values::text,"
          + " occurred_at, correlation_id, source, source_event_id, actor_ref, row_to_json(e)::text";

  private AuditRows() {}

  /** A row of the audit; {@code text} is the whole row, to check what it does not contain. */
  record Row(
      UUID administrationId,
      UUID userId,
      String entityName,
      UUID entityId,
      String operation,
      Map<String, Object> newValues,
      Instant occurredAt,
      String correlationId,
      String source,
      String sourceEventId,
      String actorRef,
      String text) {}

  /** The one row of {@code operation} with this correlation id, once the listener wrote it. */
  static Row await(String operation, String correlation) {
    List<Row> rows =
        poll(
            "SELECT "
                + COLUMNS
                + " FROM audit.audit_event e"
                + " WHERE operation = ? AND correlation_id = ?",
            operation,
            correlation);
    if (rows.size() != 1) {
      throw new AssertionError(
          "Se esperaba una fila " + operation + " con la correlación " + correlation + ": " + rows);
    }
    return rows.getFirst();
  }

  /** The rows with this correlation id, once there is at least one. */
  static List<Row> byCorrelation(String correlation) {
    return poll(
        "SELECT " + COLUMNS + " FROM audit.audit_event e WHERE correlation_id = ?", correlation);
  }

  /** The rows with this correlation id now, without waiting. */
  static List<Row> byCorrelationNow(String correlation) {
    return read(
        "SELECT " + COLUMNS + " FROM audit.audit_event e WHERE correlation_id = ?", correlation);
  }

  /** The rows of the identity provider with this source event id (none if not imported). */
  static List<Row> ofProviderEvent(String sourceEventId) {
    return read(
        "SELECT "
            + COLUMNS
            + " FROM audit.audit_event e"
            + " WHERE source = 'IDENTITY_PROVIDER' AND source_event_id = ?",
        sourceEventId);
  }

  /** Waits until {@code select} (a count) is positive and returns it. */
  static long awaitCount(String select, Object... args) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    long count;
    do {
      count = ((Number) LivDatabase.readAsOwner(select, args).getFirst()[0]).longValue();
    } while (count == 0 && pause(deadline));
    return count;
  }

  /** Waits until {@code select} (a count) is zero and returns it. */
  static long awaitZero(String select, Object... args) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    long count;
    do {
      count = ((Number) LivDatabase.readAsOwner(select, args).getFirst()[0]).longValue();
    } while (count != 0 && pause(deadline));
    return count;
  }

  /** The LIV id of the user registered with this subject. */
  static UUID userId(String subject) {
    return (UUID)
        LivDatabase.readAsOwner(
                "SELECT id FROM core.admin_user WHERE identity_subject = ?", subject)
            .getFirst()[0];
  }

  private static List<Row> poll(String select, Object... args) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    List<Row> rows;
    do {
      rows = read(select, args);
    } while (rows.isEmpty() && pause(deadline));
    return rows;
  }

  private static List<Row> read(String select, Object... args) {
    return LivDatabase.readAsOwner(select, args).stream().map(AuditRows::row).toList();
  }

  private static Row row(Object[] c) {
    return new Row(
        (UUID) c[0],
        (UUID) c[1],
        (String) c[2],
        (UUID) c[3],
        (String) c[4],
        c[5] == null ? null : JSON.readValue((String) c[5], new TypeReference<>() {}),
        instant(c[6]),
        (String) c[7],
        (String) c[8],
        (String) c[9],
        (String) c[10],
        (String) c[11]);
  }

  private static Instant instant(Object value) {
    return switch (value) {
      case Timestamp timestamp -> timestamp.toInstant();
      case OffsetDateTime dateTime -> dateTime.toInstant();
      default -> throw new IllegalStateException("Fecha inesperada: " + value);
    };
  }

  /** Sleeps a little if there is time left; false once the deadline passed. */
  private static boolean pause(Instant deadline) {
    if (Instant.now().isAfter(deadline)) {
      return false;
    }
    try {
      Thread.sleep(100);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
