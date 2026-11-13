package co.com.sapenlinea.liv.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A business rule refused an action (ADR 0035). Unchecked, so a refusal rolls the transaction back
 * without Spring trying to commit it. The domain throws it without knowing about HTTP: {@code
 * platform} turns it into a Problem Details with the rule code and the details as extensions.
 *
 * <p>The code is the one of the rule, written with the ASCII hyphen ({@code V-01}).
 */
public final class RuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Keys of the Problem Details itself: a detail cannot use them (ADR 0035). */
  private static final Set<String> RESERVED_KEYS =
      Set.of("type", "title", "status", "detail", "instance", "code");

  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]*");

  /** What kind of refusal it is, which decides the HTTP status. */
  public enum Type {
    /** The current state of what the action acts on prevents it (409). */
    CONFLICT,
    /** The request is valid but the rule refuses it (422). */
    UNPROCESSABLE
  }

  private final String code;
  private final Type type;
  private final transient Map<String, Object> details;

  /**
   * @param code code of the rule, with the ASCII hyphen
   * @param type kind of refusal
   * @param details extensions of the answer: text, number or boolean values, none of them using a
   *     key of the Problem Details
   */
  public RuleViolation(String code, Type type, Map<String, ?> details) {
    super(describe(code, type));
    this.code = code;
    this.type = type;
    this.details = copyOf(details);
  }

  /** Code of the rule that refused the action. */
  public String code() {
    return code;
  }

  /** Kind of refusal. */
  public Type type() {
    return type;
  }

  /** Extensions of the answer, in the order they were given. Never changes. */
  public Map<String, Object> details() {
    return details;
  }

  private static String describe(String code, Type type) {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(type, "type");
    if (!CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("A rule code is ASCII, without spaces: " + code);
    }
    return code + " " + type;
  }

  private static Map<String, Object> copyOf(Map<String, ?> source) {
    Objects.requireNonNull(source, "details");
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (RESERVED_KEYS.contains(key)) {
        throw new IllegalArgumentException("A detail cannot use the key " + key);
      }
      if (!(value instanceof CharSequence || value instanceof Number || value instanceof Boolean)) {
        throw new IllegalArgumentException("A detail is text, number or boolean: " + key);
      }
      copy.put(key, value);
    });
    return Collections.unmodifiableMap(copy);
  }
}
