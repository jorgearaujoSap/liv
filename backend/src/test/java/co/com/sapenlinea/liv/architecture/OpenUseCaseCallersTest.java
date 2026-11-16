package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.audit.application.RecordFiscalYearEvents;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The methods of {@code RecordFiscalYearEvents} are open use cases: they need no permission because
 * they run without a user or a request (001-16, {@code AuthorizationRules.OPEN_USE_CASES}). That is
 * safe only while nobody else can call them: they take the administration and the user from their
 * argument, so a controller or another use case that called them could write audit rows for anyone.
 * Only the listener of the events of {@code tax} depends on them.
 */
@DisplayName("Quién llama a los casos de uso abiertos de la auditoría de vigencias (007-20)")
class OpenUseCaseCallersTest {

  private static final String LISTENER =
      "co.com.sapenlinea.liv.audit.infrastructure.FiscalYearEventsListener";

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("co.com.sapenlinea.liv");

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Solo el oyente de los eventos de tax depende de RecordFiscalYearEvents")
  void onlyTheListenerCallsTheOpenUseCases() {
    assertThat(callersOfRecordFiscalYearEvents(PRODUCTION)).containsExactly(LISTENER);
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · La regla detecta a una clase ajena que dependa de RecordFiscalYearEvents")
  void theRuleSeesAnIntruder() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(RecordFiscalYearEvents.class, Intruder.class);

    assertThat(callersOfRecordFiscalYearEvents(classes)).containsExactly(Intruder.class.getName());
  }

  /** The classes, other than itself, that depend on {@code RecordFiscalYearEvents}. */
  private static Set<String> callersOfRecordFiscalYearEvents(JavaClasses classes) {
    JavaClass target = classes.get(RecordFiscalYearEvents.class);
    return target.getDirectDependenciesToSelf().stream()
        .map(dependency -> dependency.getOriginClass().getName())
        .filter(name -> !name.equals(target.getName()))
        .collect(Collectors.toSet());
  }

  /** What a controller or another use case would look like if it used the open use cases. */
  @SuppressWarnings("unused")
  private static class Intruder {

    private RecordFiscalYearEvents record;
  }
}
