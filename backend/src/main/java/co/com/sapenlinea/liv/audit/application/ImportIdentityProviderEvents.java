package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.audit.application.AuditLog.Cursor;
import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.EventsPage;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.Kind;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.ProviderEvent;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.stereotype.Service;

/**
 * Copies the events of the identity provider to the immutable audit (001-18, plan step 9, enmienda
 * 1 of the spec): failed logins, lockouts and code exchange errors, and the changes administrators
 * make to users. A scheduled platform job calls it, one replica at a time; it needs no permission.
 *
 * <ul>
 *   <li><b>Where each event goes:</b> to the administration of the user if LIV knows it, otherwise
 *       to a platform row.
 *   <li><b>What it keeps:</b> {@code result}, {@code reason}, {@code truncated_ip} and {@code
 *       masked_attempted_username} (plan, «Datos, API y seguridad»; decision of the project lead,
 *       2026-11-03), and the author as an opaque reference ({@link AuditMasking}).
 *   <li><b>Once:</b> by (source, event id), and the watermark skips the events already seen in the
 *       overlap window that each run reads again.
 *   <li><b>Nothing skipped:</b> if the provider has more events than a run reads, the watermark
 *       stays at the last one read; an event the audit rejects is kept as a minimal row instead of
 *       stopping the importer.
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
@EnableConfigurationProperties(AuditProperties.class)
public class ImportIdentityProviderEvents {

  private static final Logger LOG = LoggerFactory.getLogger(ImportIdentityProviderEvents.class);

  private static final String SOURCE = "IDENTITY_PROVIDER";
  private static final int MAX_OPERATION = 40;
  private static final int MAX_REASON = 60;
  private static final int MAX_EVENT_ID = 90;

  /** What is left out of a type and of an error code of the provider. */
  private static final Pattern NOT_OPERATION = Pattern.compile("[^A-Z_]");

  private static final Pattern NOT_REASON = Pattern.compile("[^a-z0-9_]");

  private final IdentityProviderEvents provider;
  private final AuditLog log;
  private final UserDirectory directory;
  private final AuditMasking masking;
  private final AuditProperties.Importer settings;
  private final Clock clock;
  private final String issuer;

  ImportIdentityProviderEvents(
      IdentityProviderEvents provider,
      AuditLog log,
      UserDirectory directory,
      AuditProperties properties,
      Clock clock,
      @Value("${liv.security.issuer-uri}") String issuer) {
    this.provider = provider;
    this.log = log;
    this.directory = directory;
    this.masking = new AuditMasking(properties.hmacKey());
    this.settings = properties.importer();
    this.clock = clock;
    this.issuer = issuer;
  }

  /**
   * What an import did.
   *
   * @param imported the events written to the audit (or found already there)
   * @param cursor the watermark after the import
   * @param behind whether the watermark is older than the allowed lag
   */
  public record ImportResult(int imported, Instant cursor, boolean behind) {}

  @OpenUseCase("Trabajo programado de plataforma: corre sin usuario ni petición")
  public ImportResult importNew() {
    Instant now = clock.instant();
    Cursor cursor = log.cursor().orElse(null);
    Instant from =
        cursor == null
            ? now.minus(settings.retention())
            : cursor.lastEventAt().minus(settings.overlap());
    Set<String> seen = cursor == null ? Set.of() : cursor.seenEventIds();

    int limit = settings.pageSize() * settings.maxPages();
    EventsPage users = provider.userEvents(from, limit);
    EventsPage admins = provider.adminEvents(from, limit);
    List<ProviderEvent> events = new ArrayList<>(users.events());
    events.addAll(admins.events());
    events.sort(Comparator.comparing(ProviderEvent::time));

    int imported = 0;
    for (ProviderEvent event : events) {
      if (!seen.contains(sourceEventId(event))) {
        append(event);
        imported++;
      }
    }

    Instant watermark = watermark(cursor, events, users, admins, now);
    Instant windowStart = watermark.minus(settings.overlap());
    log.saveCursor(
        new Cursor(
            watermark,
            events.stream()
                .filter(event -> !event.time().isBefore(windowStart))
                .map(ImportIdentityProviderEvents::sourceEventId)
                .collect(Collectors.toSet())));

    boolean behind = watermark.isBefore(now.minus(settings.maxLag()));
    if (behind) {
      LOG.error(
          "The importer of identity provider events is behind: watermark {}, more than {} ago",
          watermark,
          settings.maxLag());
    }
    return new ImportResult(imported, watermark, behind);
  }

  /**
   * Appends one event. If the audit rejects it as it is (a constraint), it keeps the event with its
   * result only, so a single event cannot stop the import; a database that does not respond stops
   * the run, which the next one repeats.
   */
  private void append(ProviderEvent event) {
    Entry entry = entry(event);
    try {
      log.append(entry);
    } catch (NonTransientDataAccessException e) {
      LOG.warn(
          "Identity provider event {} rejected by the audit; kept without its details",
          entry.sourceEventId(),
          e);
      log.append(
          new Entry(
              entry.source(),
              entry.sourceEventId(),
              entry.administrationId(),
              entry.userId(),
              entry.entityName(),
              null,
              entry.operation(),
              entry.occurredAt(),
              null,
              entry.actorRef(),
              Map.of("result", entry.newValues().get("result"))));
    }
  }

  /**
   * The new watermark, which never goes back and never goes past now (a provider's clock ahead
   * would hide the events up to its time):
   *
   * <ul>
   *   <li>if a stream had more events than this run read, the last event it read;
   *   <li>otherwise the newest event read, and at least now minus the overlap: with everything
   *       read, a quiet provider does not make the importer look behind, and the next run still
   *       reads the overlap again for events stored late.
   * </ul>
   */
  private Instant watermark(
      Cursor cursor, List<ProviderEvent> events, EventsPage users, EventsPage admins, Instant now) {
    Instant previous = cursor == null ? null : cursor.lastEventAt();
    Instant stoppedAt = earliest(lastOf(users), lastOf(admins));
    if (stoppedAt != null) {
      return latest(previous, earliest(stoppedAt, now));
    }
    Instant newestEvent = events.isEmpty() ? null : events.getLast().time();
    Instant caughtUp = now.minus(settings.overlap());
    return latest(previous, earliest(latest(newestEvent, caughtUp), now));
  }

  /** Where a stream with more events stopped; null if it read them all. */
  private static Instant lastOf(EventsPage page) {
    if (!page.more()) {
      return null;
    }
    return page.events().isEmpty() ? Instant.EPOCH : page.events().getLast().time();
  }

  private Entry entry(ProviderEvent event) {
    DirectoryUser user =
        event.subject() == null ? null : directory.resolve(issuer, event.subject()).orElse(null);
    return new Entry(
        SOURCE,
        sourceEventId(event),
        user == null ? null : user.administrationId(),
        user == null ? null : user.id(),
        "identity_provider",
        null,
        clip(event.type(), MAX_OPERATION, NOT_OPERATION),
        event.time(),
        null,
        event.authorSubject() == null ? null : masking.hmac(event.authorSubject()),
        values(event));
  }

  /** The whitelist of the plan: nothing of the provider is copied as it comes. */
  private Map<String, Object> values(ProviderEvent event) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("result", result(event));
    if (event.error() != null) {
      values.put("reason", clip(event.error(), MAX_REASON, NOT_REASON));
    }
    String ip = AuditMasking.truncateIp(event.ipAddress());
    if (ip != null) {
      values.put("truncated_ip", ip);
    }
    Map<String, Object> username = masking.maskUsername(event.attemptedUsername());
    if (username != null) {
      values.put("masked_attempted_username", username);
    }
    return values;
  }

  private static String result(ProviderEvent event) {
    if (event.type().contains("LOCKOUT")) {
      return "LOCKED";
    }
    boolean failed =
        event.error() != null || (event.kind() == Kind.USER && event.type().endsWith("_ERROR"));
    return failed ? "FAILURE" : "SUCCESS";
  }

  /** The id of the event in the audit; an overlong id of the provider is kept by its SHA-256. */
  private static String sourceEventId(ProviderEvent event) {
    String id = event.id().length() <= MAX_EVENT_ID ? event.id() : sha256(event.id());
    return (event.kind() == Kind.USER ? "user:" : "admin:") + id;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * A code of the provider, never free text: without the characters that {@code disallowed}
   * matches, and at most {@code max} long.
   */
  private static String clip(String value, int max, Pattern disallowed) {
    String kept = disallowed.matcher(value).replaceAll("");
    return kept.isEmpty() ? "other" : kept.substring(0, Math.min(kept.length(), max));
  }

  private static Instant earliest(Instant a, Instant b) {
    if (a == null) {
      return b;
    }
    return b == null || a.isBefore(b) ? a : b;
  }

  private static Instant latest(Instant a, Instant b) {
    if (a == null) {
      return b;
    }
    return b == null || a.isAfter(b) ? a : b;
  }
}
