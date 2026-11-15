package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which integrity failures the adapter turns into V-01: only the {@code UNIQUE (administration_id,
 * fiscal_year)}, by its SQLState and by the name of the constraint that the server reports in a
 * field of the error, whatever the language of its text. Failures built like the driver builds
 * them, because the database cannot produce another unique violation on this insert (the key is
 * generated).
 */
@DisplayName("Reconocimiento del UNIQUE de la vigencia (V-01)")
class YearKeyViolationTest {

  private static final String UNIQUE_VIOLATION = "23505";

  /** An error as the driver raises it: the fields of the server, with its message in Spanish. */
  private static DataIntegrityViolationException failure(String sqlState, String constraint) {
    String fields =
        "SERROR\0C"
            + sqlState
            + "\0Mel valor de la llave duplicada viola la restricción de unicidad"
            + (constraint == null ? "" : "\0n" + constraint)
            + "\0";
    SQLException sql = new PSQLException(new ServerErrorMessage(fields));
    return new DataIntegrityViolationException("integrity", sql);
  }

  @Test
  @Tag("V-01")
  @DisplayName(
      "V-01 · El UNIQUE del año se reconoce por su nombre aunque el mensaje esté en español")
  void theYearKeyIsRecognizedWhateverTheLanguage() {
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "administration_fiscal_year_year_key")))
        .isTrue();
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Otra restricción única, otro SQLState, sin nombre o sin driver no son V-01")
  void anythingElseIsNotV01() {
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "administration_fiscal_year_pkey")))
        .isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "administration_fiscal_year_id_administration_key")))
        .isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "other_schema.administration_fiscal_year_year_key")))
        .isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure("23503", "administration_fiscal_year_year_key")))
        .isFalse();
    assertThat(JpaFiscalYears.breaksTheYearKey(failure(UNIQUE_VIOLATION, null))).isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                new DataIntegrityViolationException(
                    "plain", new SQLException("without the driver", UNIQUE_VIOLATION))))
        .isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(new DataIntegrityViolationException("without cause")))
        .isFalse();
  }
}
