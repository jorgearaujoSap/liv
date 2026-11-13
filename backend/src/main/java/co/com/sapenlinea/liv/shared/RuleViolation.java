package co.com.sapenlinea.liv.shared;

import java.math.BigDecimal;
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

  /** Longest code the contracts allow ({@code Problem.code}). */
  private static final int MAX_CODE_LENGTH = 32;

  /**
   * Rule and criterion codes of the project ({@code V-01}, {@code CJ02}, {@code CA-1.01}, {@code
   * 007-12}): capitals and digits joined by an ASCII hyphen or a dot.
   */
  private static final Pattern CODE = Pattern.compile("[A-Z0-9]+([.-][A-Z0-9]+)*");

  /** Keys of the Problem Details itself: a detail cannot use them (ADR 0035). */
  private static final Set<String> RESERVED_KEYS =
      Set.of("type", "title", "status", "detail", "instance", "code");

  /** What kind of refusal it is, which decides the HTTP status. */
  public enum Type {
    /** The current state of what the action acts on prevents it (409). */
    CONFLICT,
    /** The request is valid but the rule refuses it (422). */
    UNPROCESSABLE
  }

  private final String code;
  private final Type type;

  /** The map is unmodifiable and holds only serializable values, so the exception serializes. */
  private final Map<String, Object> details;

  /**
   * @param code code of the rule, in capitals with the ASCII hyphen, up to 32 characters
   * @param type kind of refusal
   * @param details extensions of the answer: {@code String}, {@code Integer}, {@code Long}, {@code
   *     BigDecimal} or {@code Boolean} values, none of them using a key of the Problem Details
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
    if (code.length() > MAX_CODE_LENGTH || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException(
          "Not a rule code (capitals, digits, ASCII hyphen or dot, up to 32): " + code);
    }
    return code + " " + type;
  }

  /**
   * Only immutable values that JSON writes exactly: no {@code Double} (NaN has no JSON form) and no
   * {@code StringBuilder} or other mutable type.
   */
  private static boolean isScalar(Object value) {
    return value instanceof String
        || value instanceof Integer
        || value instanceof Long
        || value instanceof BigDecimal
        || value instanceof Boolean;
  }

  private static Map<String, Object> copyOf(Map<String, ?> source) {
    Objects.requireNonNull(source, "details");
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (key == null || key.isEmpty()) {
        throw new IllegalArgumentException("A detail key cannot be null or empty");
      }
      if (RESERVED_KEYS.contains(key)) {
        throw new IllegalArgumentException("A detail cannot use the key " + key);
      }
      if (!isScalar(value)) {
        throw new IllegalArgumentException(
            "A detail is a String, Integer, Long, BigDecimal or Boolean: " + key);
      }
      copy.put(key, value);
    });
    return Collections.unmodifiableMap(copy);
  }
}
