package co.com.sapenlinea.liv.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The immutable audit (doc. 02 §9, ERD «AUDIT»): {@code audit.audit_event}, insert only, once per
 * (source, source event id), and the watermark of the importer. Each row goes in the administration
 * it belongs to; only the two paths of ADR 0025 write platform rows (no administration).
 */
public interface AuditLog {

  /**
   * Appends a row in its administration; without one, as a platform row of the importer of the
   * identity provider, the only source allowed to (ADR 0025). Appending the same event again does
   * nothing.
   */
  void append(Entry entry);

  /**
   * Appends a refused start of a LIV session: in its administration if that administration exists,
   * otherwise, or without one, as a platform row through {@code
   * audit.record_platform_session_denied} (ADR 0025). Appending the same event again does nothing.
   */
  void appendSessionDenied(Entry entry);

  /** The watermark of the importer of the identity provider, if it ran before. */
  Optional<Cursor> cursor();

  /** Moves the watermark forward; it never goes back. */
  void saveCursor(Cursor cursor);

  /**
   * A row of the audit.
   *
   * @param source {@code LIV} or {@code IDENTITY_PROVIDER}
   * @param sourceEventId the id of the event in its source, unique with the source
   * @param administrationId the administration of the row, or null for a platform row
   * @param userId the LIV user of the event, or null
   * @param entityName what the event is about: {@code session}, {@code user} or {@code
   *     identity_provider}
   * @param entityId the id of that entity in LIV, or null
   * @param operation what happened, for example {@code SESSION_STARTED}
   * @param occurredAt when it happened at its source
   * @param correlationId the correlation id of the request of LIV, or null
   * @param actorRef an opaque reference to an actor who is not a LIV user, or null
   * @param newValues the whitelisted facts of the event: result, reason and masked data
   */
  record Entry(
      String source,
      String sourceEventId,
      UUID administrationId,
      UUID userId,
      String entityName,
      UUID entityId,
      String operation,
      Instant occurredAt,
      String correlationId,
      String actorRef,
      Map<String, Object> newValues) {

    public Entry {
      newValues = Map.copyOf(newValues);
    }
  }

  /**
   * The watermark of the importer.
   *
   * @param lastEventAt the time of the newest event imported
   * @param seenEventIds the ids of the events imported inside the overlap window, so the next
   *     import, which reads that window again, skips them
   */
  record Cursor(Instant lastEventAt, Set<String> seenEventIds) {

    public Cursor {
      seenEventIds = Set.copyOf(seenEventIds);
    }
  }
}
