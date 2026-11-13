package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The preparation checklist of a fiscal year (Doc. 01 §6.1). Pure domain: the state of the year
 * arrives as data and the points 2 to 9 come from the {@link PreparationItemCheck} port, which has
 * no implementation in feature 007.
 */
class PreparationChecklistTest {

  private static final UUID ADMINISTRATION =
      UUID.fromString("00000000-0000-0000-0000-000000000052");
  private static final UUID OTHER_ADMINISTRATION =
      UUID.fromString("00000000-0000-0000-0000-000000000099");
  private static final int YEAR = 2026;

  private static PreparationChecklist withoutChecks(boolean preparationOpened) {
    return PreparationChecklist.evaluate(ADMINISTRATION, YEAR, preparationOpened, List.of());
  }

  private static PreparationItemCheck check(PreparationPoint point, PreparationCheckResult result) {
    return new PreparationItemCheck() {
      @Override
      public PreparationPoint point() {
        return point;
      }

      @Override
      public PreparationCheckResult check(UUID administrationId, int year) {
        return result;
      }
    };
  }

  private static List<PreparationItemCheck> allRequiredMet() {
    List<PreparationItemCheck> checks = new ArrayList<>();
    for (PreparationPoint point : PreparationPoint.values()) {
      if (point.required() && point != PreparationPoint.FISCAL_YEAR_IN_PREPARATION) {
        checks.add(check(point, PreparationCheckResult.met()));
      }
    }
    return checks;
  }

  private static PreparationItem item(PreparationChecklist checklist, int number) {
    return checklist.items().get(number - 1);
  }

  // --- 007-11: the nine points of Doc. 01 §6.1

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · La lista tiene los nueve puntos del Doc. 01 §6.1, en orden, del 1 al 9")
  void hasTheNinePointsInOrder() {
    PreparationChecklist checklist = withoutChecks(true);

    assertThat(checklist.items()).hasSize(9);
    assertThat(checklist.items())
        .extracting(PreparationItem::number)
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · Cada punto trae su code del contrato (tax.yaml), por número")
  void eachPointHasItsContractCode() {
    assertThat(withoutChecks(true).items())
        .extracting(PreparationItem::code)
        .containsExactly(
            "FISCAL_YEAR_IN_PREPARATION",
            "VALUATION_RESOLUTION",
            "VALUATION_CATALOG_PUBLISHED",
            "TAX_RATES",
            "TAX_CALENDAR",
            "TAX_BENEFITS",
            "PENALTY_RULES",
            "SAMPLE_ASSESSMENT",
            "JURISDICTION_CONFIG_PUBLISHED");
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · Los puntos 1 a 5, 8 y 9 son obligatorios y el 6 y el 7 son opcionales")
  void requiredAndOptionalPoints() {
    PreparationChecklist checklist = withoutChecks(true);

    assertThat(checklist.items())
        .extracting(PreparationItem::required)
        .containsExactly(true, true, true, true, true, false, false, true, true);
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · Cada punto lleva a la opción del menú que lo atiende")
  void eachPointLinksToItsMenuOption() {
    assertThat(withoutChecks(true).items())
        .extracting(PreparationItem::option)
        .containsExactly("1.4", "1.3", "1.5.2", "1.5.5", "1.5.6", "1.5.7", "1.5.8", "3.3", "1.5.9");
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · El punto 1 no se cumple con la vigencia PLANEADA: pendiente, FISCAL_YEAR_PLANNED")
  void pointOneIsPendingWhileThePreparationIsNotOpen() {
    PreparationItem one = item(withoutChecks(false), 1);

    assertThat(one.status()).isEqualTo(PreparationItemStatus.PENDING);
    assertThat(one.reason()).isEqualTo("FISCAL_YEAR_PLANNED");
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · El punto 1 se cumple con la preparación abierta (EN_PREPARACION): MET")
  void pointOneIsMetOnceThePreparationIsOpen() {
    PreparationItem one = item(withoutChecks(true), 1);

    assertThat(one.status()).isEqualTo(PreparationItemStatus.MET);
    assertThat(one.reason()).isEqualTo("MET");
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Sin implementaciones del puerto, los puntos 2 a 9 dan OPTION_NOT_AVAILABLE, nunca"
          + " cumplido")
  void withoutImplementationsPointsTwoToNineAreNeverMet() {
    for (boolean opened : new boolean[] {false, true}) {
      PreparationChecklist checklist = withoutChecks(opened);

      for (int number = 2; number <= 9; number++) {
        PreparationItem item = item(checklist, number);
        assertThat(item.status()).as("punto %d", number).isEqualTo(PreparationItemStatus.PENDING);
        assertThat(item.reason()).as("punto %d", number).isEqualTo("OPTION_NOT_AVAILABLE");
      }
    }
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · El resultado de una implementación del puerto es el estado de su punto")
  void aCheckResultIsTheStatusOfItsPoint() {
    PreparationChecklist checklist =
        PreparationChecklist.evaluate(
            ADMINISTRATION,
            YEAR,
            true,
            List.of(
                check(PreparationPoint.VALUATION_RESOLUTION, PreparationCheckResult.met()),
                check(
                    PreparationPoint.TAX_RATES,
                    PreparationCheckResult.observed("RATE_RANGES_OVERLAP"))));

    assertThat(item(checklist, 2).status()).isEqualTo(PreparationItemStatus.MET);
    assertThat(item(checklist, 2).reason()).isEqualTo("MET");
    assertThat(item(checklist, 4).status()).isEqualTo(PreparationItemStatus.OBSERVED);
    assertThat(item(checklist, 4).reason()).isEqualTo("RATE_RANGES_OVERLAP");
    assertThat(item(checklist, 3).reason()).isEqualTo("OPTION_NOT_AVAILABLE");
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · El puerto recibe la administración y el año de la vigencia, y una vez")
  void thePortReceivesTheAdministrationAndTheYear() {
    List<UUID> administrations = new ArrayList<>();
    List<Integer> years = new ArrayList<>();
    PreparationItemCheck spy =
        new PreparationItemCheck() {
          @Override
          public PreparationPoint point() {
            return PreparationPoint.TAX_CALENDAR;
          }

          @Override
          public PreparationCheckResult check(UUID administrationId, int year) {
            administrations.add(administrationId);
            years.add(year);
            return PreparationCheckResult.met();
          }
        };

    PreparationChecklist.evaluate(OTHER_ADMINISTRATION, 2031, true, List.of(spy));

    assertThat(administrations).containsExactly(OTHER_ADMINISTRATION);
    assertThat(years).containsExactly(2031);
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Si una implementación del puerto falla, la lista falla: nunca queda cumplido")
  void aFailingCheckFailsTheWholeChecklist() {
    IllegalStateException failure = new IllegalStateException("boom");
    PreparationItemCheck failing =
        new PreparationItemCheck() {
          @Override
          public PreparationPoint point() {
            return PreparationPoint.TAX_RATES;
          }

          @Override
          public PreparationCheckResult check(UUID administrationId, int year) {
            throw failure;
          }
        };

    assertThatThrownBy(
            () -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, List.of(failing)))
        .isSameAs(failure);
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · El punto 1 no sale del puerto: una implementación para él se rechaza")
  void pointOneCannotBeProvidedByThePort() {
    List<PreparationItemCheck> checks =
        List.of(check(PreparationPoint.FISCAL_YEAR_IN_PREPARATION, PreparationCheckResult.met()));

    assertThatThrownBy(() -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, false, checks))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · Dos implementaciones para el mismo punto se rechazan, no se elige una")
  void twoChecksForTheSamePointAreRejected() {
    List<PreparationItemCheck> checks =
        List.of(
            check(PreparationPoint.TAX_RATES, PreparationCheckResult.met()),
            check(PreparationPoint.TAX_RATES, PreparationCheckResult.pending("EMPTY")));

    assertThatThrownBy(() -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, checks))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Un valor nulo (administración, lista, elemento, punto o resultado) se rechaza")
  void nullInputsAreRejected() {
    assertThatThrownBy(() -> PreparationChecklist.evaluate(null, YEAR, true, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, null))
        .isInstanceOf(NullPointerException.class);

    List<PreparationItemCheck> withNullElement = Arrays.asList((PreparationItemCheck) null);
    assertThatThrownBy(
            () -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, withNullElement))
        .isInstanceOf(NullPointerException.class);

    List<PreparationItemCheck> withoutPoint = List.of(check(null, PreparationCheckResult.met()));
    assertThatThrownBy(
            () -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, withoutPoint))
        .isInstanceOf(NullPointerException.class);

    List<PreparationItemCheck> returnsNull = List.of(check(PreparationPoint.TAX_CALENDAR, null));
    assertThatThrownBy(() -> PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, returnsNull))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("point 5");
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · La lista que se entrega no se puede modificar")
  void theItemsAreImmutable() {
    PreparationChecklist checklist = withoutChecks(true);

    assertThatThrownBy(() -> checklist.items().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> checklist.missing().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Un punto se arma con un resultado ya validado, no con un estado y un motivo sueltos")
  void anItemIsBuiltFromAValidatedResult() {
    PreparationItem item =
        new PreparationItem(PreparationPoint.TAX_RATES, PreparationCheckResult.pending("EMPTY"));

    assertThat(item.status()).isEqualTo(PreparationItemStatus.PENDING);
    assertThat(item.reason()).isEqualTo("EMPTY");
    assertThatThrownBy(() -> new PreparationItem(PreparationPoint.TAX_RATES, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PreparationItem(null, PreparationCheckResult.met()))
        .isInstanceOf(NullPointerException.class);
  }

  // --- V-03 · CA-14 · 007-12: enabling

  @Test
  @Tag("V-03")
  @Tag("CA-14")
  @Tag("007-12")
  @DisplayName(
      "V-03 · CA-14 · 007-12 · Sin implementaciones del puerto (la 007), canEnable() es falso")
  void canEnableIsFalseWithoutImplementations() {
    assertThat(withoutChecks(false).canEnable()).isFalse();
    assertThat(withoutChecks(true).canEnable()).isFalse();
  }

  @Test
  @Tag("V-03")
  @Tag("CA-14")
  @Tag("007-12")
  @DisplayName(
      "V-03 · CA-14 · 007-12 · missing lista los obligatorios que faltan: 2, 3, 4, 5, 8 y 9 con la"
          + " vigencia EN_PREPARACION")
  void missingListsTheRequiredPointsThatAreNotMet() {
    assertThat(withoutChecks(true).missing()).containsExactly(2, 3, 4, 5, 8, 9);
  }

  @Test
  @Tag("V-03")
  @Tag("CA-14")
  @Tag("007-12")
  @DisplayName("V-03 · CA-14 · 007-12 · Con la vigencia PLANEADA falta también el punto 1")
  void missingIncludesPointOneWhilePlanned() {
    assertThat(withoutChecks(false).missing()).containsExactly(1, 2, 3, 4, 5, 8, 9);
  }

  @Test
  @Tag("V-03")
  @Tag("CA-14")
  @Tag("007-12")
  @DisplayName("V-03 · CA-14 · 007-12 · Los opcionales (6 y 7) nunca están en missing")
  void optionalPointsAreNeverMissing() {
    assertThat(withoutChecks(false).missing()).doesNotContain(6, 7);
    assertThat(withoutChecks(true).missing()).doesNotContain(6, 7);
  }

  @Test
  @Tag("V-03")
  @DisplayName(
      "V-03 · canEnable() es verdadero solo con todos los obligatorios cumplidos, aunque los"
          + " opcionales no lo estén")
  void canEnableOnlyWithEveryRequiredPointMet() {
    PreparationChecklist checklist =
        PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, allRequiredMet());

    assertThat(checklist.canEnable()).isTrue();
    assertThat(checklist.missing()).isEmpty();
    assertThat(item(checklist, 6).status()).isEqualTo(PreparationItemStatus.PENDING);
    assertThat(item(checklist, 7).status()).isEqualTo(PreparationItemStatus.PENDING);
  }

  @Test
  @Tag("V-03")
  @DisplayName("V-03 · Un opcional con observación no bloquea ni aparece en missing")
  void anObservedOptionalPointDoesNotBlock() {
    List<PreparationItemCheck> checks = allRequiredMet();
    checks.add(check(PreparationPoint.TAX_BENEFITS, PreparationCheckResult.observed("NO_RULES")));
    checks.add(check(PreparationPoint.PENALTY_RULES, PreparationCheckResult.observed("NO_RULES")));

    PreparationChecklist checklist =
        PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, checks);

    assertThat(checklist.canEnable()).isTrue();
    assertThat(checklist.missing()).isEmpty();
  }

  @Test
  @Tag("V-03")
  @DisplayName(
      "V-03 · Con todo cumplido pero la vigencia PLANEADA, canEnable() es falso: falta el punto 1")
  void canEnableIsFalseWhilePlannedEvenWithEveryOtherPointMet() {
    PreparationChecklist checklist =
        PreparationChecklist.evaluate(ADMINISTRATION, YEAR, false, allRequiredMet());

    assertThat(checklist.canEnable()).isFalse();
    assertThat(checklist.missing()).containsExactly(1);
  }

  @Test
  @Tag("V-03")
  @DisplayName("V-03 · Un obligatorio con observación no cuenta como cumplido")
  void anObservedRequiredPointBlocksTheEnabling() {
    List<PreparationItemCheck> checks = allRequiredMet();
    checks.removeIf(c -> c.point() == PreparationPoint.TAX_CALENDAR);
    checks.add(
        check(PreparationPoint.TAX_CALENDAR, PreparationCheckResult.observed("NO_DEADLINE")));

    PreparationChecklist checklist =
        PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, checks);

    assertThat(checklist.canEnable()).isFalse();
    assertThat(checklist.missing()).containsExactly(5);
  }

  @Test
  @Tag("V-03")
  @DisplayName(
      "V-03 · Un obligatorio pendiente por una implementación tampoco cuenta como cumplido")
  void aPendingRequiredPointBlocksTheEnabling() {
    List<PreparationItemCheck> checks = allRequiredMet();
    checks.removeIf(c -> c.point() == PreparationPoint.JURISDICTION_CONFIG_PUBLISHED);
    checks.add(
        check(
            PreparationPoint.JURISDICTION_CONFIG_PUBLISHED,
            PreparationCheckResult.pending("CONFIG_NOT_PUBLISHED")));

    PreparationChecklist checklist =
        PreparationChecklist.evaluate(ADMINISTRATION, YEAR, true, checks);

    assertThat(checklist.canEnable()).isFalse();
    assertThat(checklist.missing()).containsExactly(9);
  }

  // --- The result of a check: the reason is a code of the contract (maxLength 64)

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · MET lleva siempre el motivo MET y los demás estados, un motivo propio")
  void resultReasons() {
    assertThat(PreparationCheckResult.met().reason()).isEqualTo("MET");
    assertThat(PreparationCheckResult.met().status()).isEqualTo(PreparationItemStatus.MET);
    assertThat(PreparationCheckResult.pending("EMPTY").status())
        .isEqualTo(PreparationItemStatus.PENDING);
    assertThat(PreparationCheckResult.observed("OVERLAP").status())
        .isEqualTo(PreparationItemStatus.OBSERVED);
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Un motivo que no es un código (vacío, minúsculas, espacios, más de 64) o MET en un"
          + " no cumplido se rechaza")
  void invalidReasonsAreRejected() {
    for (String reason :
        List.of(
            "",
            "  ",
            "x".repeat(10),
            "HAS SPACE",
            "Pending",
            "A-B",
            "_LEADING",
            "AÑO",
            "A\n",
            "1_STARTS_WITH_DIGIT",
            "X".repeat(65),
            "MET")) {
      assertThatThrownBy(() -> PreparationCheckResult.pending(reason))
          .as("pending(%s)", reason)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> PreparationCheckResult.observed(reason))
          .as("observed(%s)", reason)
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> PreparationCheckResult.pending(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · El motivo admite hasta 64 caracteres del contrato: 63 y 64 sí, 65 no")
  void reasonLengthBoundary() {
    assertThat(PreparationCheckResult.pending("X".repeat(63)).reason()).hasSize(63);
    assertThat(PreparationCheckResult.pending("X".repeat(64)).reason()).hasSize(64);
    assertThatThrownBy(() -> PreparationCheckResult.pending("X".repeat(65)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · Solo un punto cumplido lleva el motivo MET: MET con otro motivo se rechaza")
  void metWithAnotherReasonIsRejected() {
    assertThatThrownBy(() -> new PreparationCheckResult(PreparationItemStatus.MET, "OTHER"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PreparationCheckResult(PreparationItemStatus.PENDING, "MET"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PreparationCheckResult(PreparationItemStatus.OBSERVED, "MET"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
