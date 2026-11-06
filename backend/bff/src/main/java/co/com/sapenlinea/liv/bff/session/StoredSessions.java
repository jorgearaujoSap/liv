package co.com.sapenlinea.liv.bff.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The sessions of the BFF read straight from the tables of Spring Session, without a request (T19):
 * what ends a session that nobody is using, either because it expired ({@link ExpiredSessions}) or
 * because the provider closed it ({@link ProviderLogout}). A session is read locked ({@code FOR
 * UPDATE SKIP LOCKED}) inside a transaction, so two replicas never end the same one at once; its
 * attributes are deserialized here, so one that cannot be (a class changed in a deployment) can
 * still be deleted.
 */
public final class StoredSessions {

  private static final String SECURITY_CONTEXT =
      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

  private final JdbcOperations jdbc;
  private final TransactionOperations transaction;
  private final String table;
  private final DeserializingConverter deserializer =
      new DeserializingConverter(StoredSessions.class.getClassLoader());

  /**
   * @param table the table of Spring Session, {@code spring.session.jdbc.table-name}; its
   *     attributes are in the one with the suffix {@code _ATTRIBUTES}
   */
  public StoredSessions(DataSource dataSource, String table) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    this.table = table;
  }

  /** A session as its table has it. Its {@code expiry_time} is the end by inactivity. */
  record Row(String primaryId, String principal, long idleEnd) {}

  /** What the work returns, in one transaction: the locks it takes last until it ends. */
  <T> T inTransaction(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  /** The session, locked until the transaction ends; empty if it is gone or someone has it. */
  Optional<Row> lock(String sessionId) {
    return jdbc
        .query(
            "SELECT primary_id, principal_name, expiry_time FROM "
                + table
                + " WHERE session_id = ? FOR UPDATE SKIP LOCKED",
            (row, i) -> new Row(row.getString(1), row.getString(2), row.getLong(3)),
            sessionId)
        .stream()
        .findFirst();
  }

  /**
   * The session, locked until the transaction ends, waiting up to {@code timeout} if someone else
   * has it (a request of the person saving it, the sweep): empty only if it is gone.
   *
   * @throws org.springframework.dao.DataAccessException if it is still locked after the timeout
   */
  Optional<Row> lockWaiting(String sessionId, Duration timeout) {
    jdbc.execute("SET LOCAL lock_timeout = '" + timeout.toMillis() + "ms'");
    return jdbc
        .query(
            "SELECT primary_id, principal_name, expiry_time FROM "
                + table
                + " WHERE session_id = ? FOR UPDATE",
            (row, i) -> new Row(row.getString(1), row.getString(2), row.getLong(3)),
            sessionId)
        .stream()
        .findFirst();
  }

  /** Whether the session of a person is expired by inactivity, without reading it. */
  boolean isExpired(String sessionId, long now) {
    return !jdbc.query(
            "SELECT 1 FROM "
                + table
                + " WHERE session_id = ? AND principal_name IS NOT NULL AND expiry_time < ?",
            (row, i) -> 1,
            sessionId,
            now)
        .isEmpty();
  }

  /**
   * Sessions of a person expired by inactivity, or created before {@code createdBefore}, after the
   * given one in the order of creation: by key, not by position, so the ones that are ended and
   * leave the table do not move the others.
   */
  List<Candidate> candidates(
      long now, long createdBefore, long afterCreation, String afterId, int limit) {
    return jdbc.query(
        "SELECT session_id, creation_time FROM "
            + table
            + " WHERE principal_name IS NOT NULL AND (expiry_time < ? OR creation_time < ?)"
            + " AND (creation_time, session_id) > (?, ?)"
            + " ORDER BY creation_time, session_id LIMIT ?",
        (row, i) -> new Candidate(row.getString(1), row.getLong(2)),
        now,
        createdBefore,
        afterCreation,
        afterId,
        limit);
  }

  record Candidate(String sessionId, long creationTime) {}

  /** Deletes up to {@code limit} expired sign ins that were never finished; how many it deleted. */
  int deleteUnfinishedSignIns(long now, int limit) {
    return jdbc.update(
        "DELETE FROM "
            + table
            + " WHERE primary_id IN (SELECT primary_id FROM "
            + table
            + " WHERE principal_name IS NULL AND expiry_time < ? LIMIT ?)",
        now,
        limit);
  }

  /** The other sessions of the same person still open by inactivity. */
  List<String> otherOpenSessions(Row row, long now) {
    return jdbc.query(
        "SELECT primary_id FROM "
            + table
            + " WHERE principal_name = ? AND primary_id <> ? AND expiry_time >= ?",
        (other, i) -> other.getString(1),
        row.principal(),
        row.primaryId(),
        now);
  }

  /** The attributes the end of a session needs, as Spring Session stores them, serialized. */
  Map<String, byte[]> attributesOf(String primaryId) {
    Map<String, byte[]> attributes = new HashMap<>();
    jdbc.query(
        "SELECT attribute_name, attribute_bytes FROM "
            + table
            + "_ATTRIBUTES WHERE session_primary_id = ? AND attribute_name IN (?, ?, ?)",
        row -> {
          attributes.put(row.getString(1), row.getBytes(2));
        },
        primaryId,
        SECURITY_CONTEXT,
        SessionLifetime.STARTED_AT_ATTRIBUTE,
        EncryptedAuthorizedClientRepository.ATTRIBUTE);
    return attributes;
  }

  /** The person of LIV of the session; {@code null} if it has none. It throws if unreadable. */
  SessionAuthentication personOf(Map<String, byte[]> attributes) {
    if (!(read(attributes, SECURITY_CONTEXT) instanceof SecurityContext context)) {
      return null;
    }
    return context.getAuthentication() instanceof SessionAuthentication person ? person : null;
  }

  /** The encrypted tokens, or {@code null} if they cannot be read: then they cannot be revoked. */
  Object tokensOf(Map<String, byte[]> attributes) {
    try {
      return read(attributes, EncryptedAuthorizedClientRepository.ATTRIBUTE);
    } catch (RuntimeException unreadable) {
      return null;
    }
  }

  /** An attribute, deserialized; {@code null} if the session lacks it. It throws if unreadable. */
  Object read(Map<String, byte[]> attributes, String name) {
    byte[] bytes = attributes.get(name);
    return bytes == null ? null : deserializer.convert(bytes);
  }

  /** Deletes the session and its attributes; whether it was still there. */
  boolean delete(String primaryId) {
    return jdbc.update("DELETE FROM " + table + " WHERE primary_id = ?", primaryId) == 1;
  }

  /**
   * A short fingerprint of an identifier, for the log: enough to match it by hand with the audit,
   * without writing the identifier itself.
   */
  static String fingerprint(String identifier) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(identifier.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 6);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
