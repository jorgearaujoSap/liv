package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which integrity failures the adapter turns into V-01: only the {@code UNIQUE (administration_id,
 * fiscal_year)}, by its SQLState and its constraint name. Synthetic failures, because the database
 * cannot produce another unique violation on this insert (the key is generated).
 */
@DisplayName("Reconocimiento del UNIQUE de la vigencia (V-01)")
class YearKeyViolationTest {

  private static final String UNIQUE_VIOLATION = "23505";

  private static DataIntegrityViolationException failure(String sqlState, String constraint) {
    SQLException sql = new SQLException("failure", sqlState);
    return new DataIntegrityViolationException(
        "integrity", new ConstraintViolationException("failure", sql, constraint));
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · El UNIQUE del año se reconoce por su SQLState y su nombre de restricción")
  void theYearKeyIsRecognized() {
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "administration_fiscal_year_year_key")))
        .isTrue();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                failure(UNIQUE_VIOLATION, "tax.administration_fiscal_year_year_key")))
        .isTrue();
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Otra restricción única, otro SQLState o un fallo sin SQL no son V-01")
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
                failure("23503", "administration_fiscal_year_year_key")))
        .isFalse();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(new DataIntegrityViolationException("without cause")))
        .isFalse();
  }

  @Test
  @Tag("V-01")
  @DisplayName(
      "V-01 · Un 23505 sin nombre de restricción (el driver no lo dio) se toma por el UNIQUE del año")
  void aUniqueViolationWithoutANameIsTheYearKey() {
    // The only other unique constraints of the table depend on the id that the database generates
    // with uuidv7(), which an insert cannot repeat.
    assertThat(JpaFiscalYears.breaksTheYearKey(failure(UNIQUE_VIOLATION, null))).isTrue();
  }
}
