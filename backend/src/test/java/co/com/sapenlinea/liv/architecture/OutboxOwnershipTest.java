package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Only Spring Modulith touches {@code platform.event_publication}: {@code liv_app} may delete its
 * rows (to purge the completed deliveries, T15), and a delete of a pending delivery written by hand
 * would lose its audit. No code of LIV names the table; only the migrations do.
 */
@DisplayName("El outbox es solo de Spring Modulith (T15)")
class OutboxOwnershipTest {

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Ningún código de LIV nombra platform.event_publication")
  void noCodeNamesTheOutboxTable() throws IOException {
    List<Path> naming;
    try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
      naming =
          files.filter(Files::isRegularFile).filter(OutboxOwnershipTest::namesTheOutbox).toList();
    }

    assertThat(naming).isEmpty();
  }

  private static boolean namesTheOutbox(Path file) {
    try {
      return Files.readString(file).toLowerCase().contains("event_publication");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
