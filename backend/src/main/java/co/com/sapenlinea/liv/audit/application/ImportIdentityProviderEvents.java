package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.audit.application.AuditLog.Cursor;
import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Copies the events of the identity provider to the immutable audit (001-18, plan step 9, enmienda
 * 1 of the spec): failed logins, lockouts and code exchange errors, and the changes administrators
 * make to users. A scheduled platform job calls it, one replica at a time; it needs no permission.
 *
 * <ul>
 *   <li><b>Where each event goes:</b> to the administration of the user if LIV knows it, otherwise
 *       to a platform row.
 *   <li><b>What it keeps:</b> the facts of a whitelist, with the attempted username as an HMAC and
 *       its length, the address truncated and the author as an opaque reference ({@link
 *       AuditMasking}).
 *   <li><b>Once:</b> by (source, event id), and the watermark skips the events already seen in the
 *       overlap window that each run reads again.
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

    StreamRead users = read(provider::userEvents, from);
    StreamRead admins = read(provider::adminEvents, from);
    List<ProviderEvent> events = new ArrayList<>(users.events());
    events.addAll(admins.events());
    events.sort(Comparator.comparing(ProviderEvent::time));

    int imported = 0;
    for (ProviderEvent event : events) {
      if (!seen.contains(sourceEventId(event))) {
        log.append(entry(event));
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

  /** The events of one stream since {@code from}, page by page, up to the pages of a run. */
  private StreamRead read(BiFunction<Instant, Integer, List<ProviderEvent>> stream, Instant from) {
    List<ProviderEvent> paged = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    Instant next = from;
    for (int page = 0; page < settings.maxPages(); page++) {
      List<ProviderEvent> got = stream.apply(next, settings.pageSize());
      for (ProviderEvent event : got) {
        if (ids.add(sourceEventId(event))) {
          paged.add(event);
        }
      }
      if (got.size() < settings.pageSize()) {
        return new StreamRead(paged, null);
      }
      Instant last = got.getLast().time();
      if (!last.isAfter(next)) {
        // A full page at a single instant: it cannot move on. The next run reads it again.
        LOG.warn("Identity provider events: a full page at {}; continuing next run", last);
        return new StreamRead(paged, last);
      }
      next = last;
    }
    return new StreamRead(paged, next);
  }

  /**
   * The new watermark, which never goes back:
   *
   * <ul>
   *   <li>if a stream stopped with events left, where it stopped;
   *   <li>otherwise the newest event read, and at least now minus the overlap: with everything
   *       read, a quiet provider does not make the importer look behind, and the next run still
   *       reads the overlap again for events stored late.
   * </ul>
   */
  private Instant watermark(
      Cursor cursor, List<ProviderEvent> events, StreamRead users, StreamRead admins, Instant now) {
    Instant previous = cursor == null ? null : cursor.lastEventAt();
    Instant stoppedAt = earliest(users.stoppedAt(), admins.stoppedAt());
    if (stoppedAt != null) {
      return latest(previous, stoppedAt);
    }
    Instant newestEvent = events.isEmpty() ? null : events.getLast().time();
    Instant caughtUp = now.minus(settings.overlap());
    return latest(previous, latest(newestEvent, caughtUp));
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

  /** The whitelist of facts of an event: nothing of the provider is copied as it comes. */
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
    if (!event.roles().isEmpty()) {
      values.put("roles", event.roles());
    }
    if (event.enabled() != null) {
      values.put("enabled", event.enabled());
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

  /**
   * The events read from one stream.
   *
   * @param stoppedAt where the stream stopped with events left, or null if it read them all
   */
  private record StreamRead(List<ProviderEvent> events, Instant stoppedAt) {}
}
