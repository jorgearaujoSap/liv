package co.com.sapenlinea.liv.bff.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Size limits that keep classes focused (CC-11). Unlike a legacy code base, there are no
 * exceptions: a class that grows past a limit is split, not added to an allow list.
 */
class CodeSizeGuardTest {

  private static final Path SOURCES = Path.of("src", "main", "java");
  private static final int MAX_CLASS_LINES = 400;
  private static final int MAX_CONTROLLER_LINES = 200;
  private static final int MAX_CONSTRUCTOR_DEPENDENCIES = 8;

  @Test
  @DisplayName("Ninguna clase supera 400 líneas ni ningún controlador 200 (CC-11)")
  void classesStaySmall() throws IOException {
    Map<String, Long> oversized = new TreeMap<>();
    try (Stream<Path> files = Files.walk(SOURCES)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        long lines;
        try (Stream<String> fileLines = Files.lines(file)) {
          lines = fileLines.count();
        }
        int limit =
            file.getFileName().toString().endsWith("Controller.java")
                ? MAX_CONTROLLER_LINES
                : MAX_CLASS_LINES;
        if (lines > limit) {
          oversized.put(SOURCES.relativize(file).toString(), lines);
        }
      }
    }
    assertThat(oversized).as("clases demasiado largas").isEmpty();
  }

  @Test
  @DisplayName("Ninguna clase recibe más de 8 dependencias por constructor (CC-11)")
  void classesHaveFewDependencies() {
    Map<String, Integer> overloaded = new TreeMap<>();
    for (JavaClass javaClass :
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("co.com.sapenlinea.liv")) {
      int dependencies =
          javaClass.getConstructors().stream()
              .mapToInt(constructor -> constructor.getRawParameterTypes().size())
              .max()
              .orElse(0);
      if (dependencies > MAX_CONSTRUCTOR_DEPENDENCIES && !javaClass.isRecord()) {
        overloaded.put(javaClass.getName(), dependencies);
      }
    }
    assertThat(overloaded).as("clases con demasiadas dependencias").isEmpty();
  }
}
