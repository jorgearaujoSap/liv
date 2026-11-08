package co.com.sapenlinea.liv.contracts;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BigIntegerNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Loads a jurisdiction configuration with the portable YAML subset (feature 004, plan "Subconjunto
 * portable de YAML"; ADR 0032): what YAML 1.1, YAML 1.2 and Jackson read the same way, and nothing
 * else. It reads the parser events, so nothing is constructed before every rule has been checked.
 * Test code only: the production loader belongs to the option 1.5.9 and must pass the same common
 * cases ({@code 1.0.casos.json}).
 */
final class SafeYamlLoader {

  /**
   * Why a document is rejected. The names are the codes of the field {@code motivo} of the common
   * cases, which the root validator gives too, so they stay in Spanish like the cases.
   */
  enum Reason {
    CODIFICACION,
    TAMANO,
    SINTAXIS,
    CARACTER_DE_CONTROL,
    DIRECTIVA,
    DOCUMENTOS,
    ETIQUETA,
    ANCLA,
    ALIAS,
    FUSION,
    CLAVE_NO_TEXTO,
    CLAVE_REPETIDA,
    ESCALAR_AMBIGUO,
    VALOR_VACIO,
    PROFUNDIDAD,
    NUMERO_LARGO
  }

  /** The document is outside the portable subset. */
  static final class UnsafeYamlException extends RuntimeException {

    private final Reason reason;

    UnsafeYamlException(Reason reason, String detail) {
      // One line: a fragment of the YAML (a tag, a key) can never start a line of a log.
      super(reason + ": " + VALUE_CONTROL.matcher(detail).replaceAll(" ").replaceAll("\\s+", " "));
      this.reason = reason;
    }

    Reason reason() {
      return reason;
    }
  }

  /**
   * Control characters anywhere in the text, comments included, except the line breaks LF and CR:
   * NEL, U+2028 and U+2029 are line breaks in YAML 1.1 and not in 1.2, the file is UTF-8 without a
   * byte order mark, and U+FFFE and U+FFFF are not characters.
   */
  private static final Pattern RAW_CONTROL =
      Pattern.compile(
          "[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u2028\\u2029\\uFEFF\\uFFFE\\uFFFF]");

  /** The same characters in a key or a value, after escapes and block scalars, line breaks too. */
  private static final Pattern VALUE_CONTROL =
      Pattern.compile("[\\x00-\\x1F\\x7F-\\x9F\\u2028\\u2029\\uFEFF\\uFFFE\\uFFFF]");

  /** Limits of the subset, the same in the root validator. */
  static final int MAX_BYTES = 1024 * 1024;

  static final int MAX_DEPTH = 20;
  static final int MAX_DIGITS = 30;

  private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");
  private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)\\.[0-9]+");
  private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

  /** Plain scalars that some reader takes for a number, a date, a boolean or null. */
  private static final Pattern AMBIGUOUS_START = Pattern.compile("[-+]?\\.?[0-9]|[-+]?\\.");

  private static final Set<String> AMBIGUOUS_WORDS =
      Set.of("y", "n", "yes", "no", "on", "off", "true", "false", "null", "~");

  /** Merge key and value key of YAML 1.1. */
  private static final Set<String> MERGE_KEYS = Set.of("<<", "=");

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private SafeYamlLoader() {}

  /** Loads a file: first its size, before reading it, and then its encoding. */
  static JsonNode load(Path file) throws IOException {
    if (Files.size(file) > MAX_BYTES) {
      throw new UnsafeYamlException(Reason.TAMANO, "el archivo pasa de " + MAX_BYTES + " bytes");
    }
    String text;
    try {
      ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(file));
      text = StandardCharsets.UTF_8.newDecoder().decode(bytes).toString();
    } catch (CharacterCodingException e) {
      throw new UnsafeYamlException(Reason.CODIFICACION, "el archivo no es UTF-8 válido");
    }
    return load(text);
  }

  /**
   * Loads a text with the rules in their order: size, control characters, syntax and then, in the
   * order of the document, the rules of each event.
   */
  static JsonNode load(String text) {
    if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new UnsafeYamlException(Reason.TAMANO, "el texto pasa de " + MAX_BYTES + " bytes");
    }
    if (RAW_CONTROL.matcher(text).find()) {
      throw new UnsafeYamlException(
          Reason.CARACTER_DE_CONTROL, "el texto tiene un carácter de control");
    }
    // All the events first: a syntax error wins over any other rule, as in the root validator.
    List<Event> events = new ArrayList<>();
    try {
      new Yaml(new LoaderOptions()).parse(new StringReader(text)).forEach(events::add);
    } catch (YAMLException e) {
      // One line, so that a fragment of the YAML can never start a line of a log.
      String detail = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
      throw new UnsafeYamlException(Reason.SINTAXIS, detail);
    }
    return new Reader(events.iterator()).stream();
  }

  /** Walks the events of one stream and builds the tree. */
  private record Reader(Iterator<Event> events) {

    JsonNode stream() {
      JsonNode document = null;
      int documents = 0;
      while (events.hasNext()) {
        Event event = events.next();
        if (event instanceof DocumentStartEvent start) {
          if (start.getVersion() != null
              || (start.getTags() != null && !start.getTags().isEmpty())) {
            throw unsafe(Reason.DIRECTIVA, "directiva %YAML o %TAG", event);
          }
          if (++documents > 1) {
            throw unsafe(Reason.DOCUMENTOS, "más de un documento", event);
          }
          document = node(events.next(), 0);
        }
      }
      if (documents == 0) {
        throw new UnsafeYamlException(Reason.DOCUMENTOS, "el archivo no tiene ningún documento");
      }
      return document;
    }

    private JsonNode node(Event event, int parentDepth) {
      checkNode(event);
      if (event instanceof ScalarEvent scalar) {
        return scalar(scalar);
      }
      int depth = parentDepth + 1;
      if (depth > MAX_DEPTH) {
        throw unsafe(Reason.PROFUNDIDAD, "más de " + MAX_DEPTH + " niveles de colecciones", event);
      }
      if (event instanceof SequenceStartEvent) {
        ArrayNode array = NODES.arrayNode();
        for (Event next = events.next();
            !(next instanceof SequenceEndEvent);
            next = events.next()) {
          array.add(node(next, depth));
        }
        return array;
      }
      if (event instanceof MappingStartEvent) {
        return mapping(depth);
      }
      throw unsafe(Reason.SINTAXIS, "evento inesperado " + event, event);
    }

    private ObjectNode mapping(int depth) {
      ObjectNode object = NODES.objectNode();
      for (Event key = events.next(); !(key instanceof MappingEndEvent); key = events.next()) {
        checkNode(key);
        if (!(key instanceof ScalarEvent scalar)) {
          throw unsafe(Reason.CLAVE_NO_TEXTO, "una clave es una lista o un objeto", key);
        }
        String name = scalar.getValue();
        checkControl(name, key);
        if (scalar.getScalarStyle() == ScalarStyle.PLAIN) {
          if (MERGE_KEYS.contains(name)) {
            throw unsafe(Reason.FUSION, "clave de fusión " + name, key);
          }
          if (!isPlainText(name)) {
            throw unsafe(Reason.CLAVE_NO_TEXTO, "la clave " + name + " no es texto", key);
          }
        }
        if (object.has(name)) {
          throw unsafe(Reason.CLAVE_REPETIDA, "clave repetida " + name, key);
        }
        object.set(name, node(events.next(), depth));
      }
      return object;
    }

    private JsonNode scalar(ScalarEvent scalar) {
      String value = scalar.getValue();
      checkControl(value, scalar);
      if (scalar.getScalarStyle() != ScalarStyle.PLAIN) {
        return NODES.stringNode(value);
      }
      if (value.equals("true") || value.equals("false")) {
        return NODES.booleanNode(value.equals("true"));
      }
      if (value.equals("null")) {
        return NODES.nullNode();
      }
      boolean integer = INTEGER.matcher(value).matches();
      if (integer || DECIMAL.matcher(value).matches()) {
        if (value.chars().filter(Character::isDigit).count() > MAX_DIGITS) {
          throw unsafe(
              Reason.NUMERO_LARGO, "un número de más de " + MAX_DIGITS + " dígitos", scalar);
        }
        return integer
            ? BigIntegerNode.valueOf(new BigInteger(value))
            : DecimalNode.valueOf(new BigDecimal(value));
      }
      if (DATE.matcher(value).matches()) {
        return NODES.stringNode(value);
      }
      if (value.isEmpty()) {
        throw unsafe(Reason.VALOR_VACIO, "valor vacío; si es nulo, se escribe null", scalar);
      }
      if (MERGE_KEYS.contains(value)) {
        throw unsafe(Reason.FUSION, "valor " + value, scalar);
      }
      if (!isPlainText(value)) {
        throw unsafe(Reason.ESCALAR_AMBIGUO, value + " se lee distinto; va entre comillas", scalar);
      }
      return NODES.stringNode(value);
    }

    private static void checkNode(Event event) {
      if (event instanceof AliasEvent) {
        throw unsafe(Reason.ALIAS, "alias", event);
      }
      if (event instanceof NodeEvent node && node.getAnchor() != null) {
        throw unsafe(Reason.ANCLA, "ancla &" + node.getAnchor(), event);
      }
      String tag =
          event instanceof ScalarEvent scalar
              ? scalar.getTag()
              : event instanceof CollectionStartEvent collection ? collection.getTag() : null;
      if (tag != null) {
        throw unsafe(Reason.ETIQUETA, "etiqueta " + tag, event);
      }
    }

    private static void checkControl(String value, Event event) {
      if (VALUE_CONTROL.matcher(value).find()) {
        throw unsafe(Reason.CARACTER_DE_CONTROL, "un carácter de control en un valor", event);
      }
    }

    /** A plain scalar that every reader takes for text, and not for a date. */
    private static boolean isPlainText(String value) {
      return !value.isEmpty()
          && !AMBIGUOUS_WORDS.contains(value.toLowerCase(Locale.ROOT))
          && !MERGE_KEYS.contains(value)
          && !AMBIGUOUS_START.matcher(value).lookingAt();
    }

    private static UnsafeYamlException unsafe(Reason reason, String detail, Event event) {
      Mark mark = event.getStartMark();
      String where = mark == null ? "" : " (línea " + (mark.getLine() + 1) + ")";
      return new UnsafeYamlException(reason, detail + where);
    }
  }
}
