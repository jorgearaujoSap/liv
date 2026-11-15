package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which integrity failures of the {@code INSERT} the adapter turns into V-01: the standard SQLState
 * {@code 23505} (unique violation), wherever it is in the chain of causes, and nothing else. It
 * does not read the name of the constraint (that needs the driver or the text of the server, which
 * depends on its language): {@code FiscalYearSchemaTest} fixes that the year key is the only unique
 * constraint that an insert of the application can break.
 */
@DisplayName("Reconocimiento del UNIQUE de la vigencia por su SQLState (V-01)")
class YearKeyViolationTest {

  private static final String UNIQUE_VIOLATION = "23505";

  private static DataIntegrityViolationException failure(String sqlState) {
    SQLException sql = new SQLException("failure of the server, in any language", sqlState);
    return new DataIntegrityViolationException(
        "integrity", new ConstraintViolationException("failure", sql, "any_name"));
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Una violación de unicidad (SQLState 23505) se reconoce, sin leer su nombre")
  void aUniqueViolationIsRecognized() {
    assertThat(JpaFiscalYears.breaksTheYearKey(failure(UNIQUE_VIOLATION))).isTrue();
    assertThat(
            JpaFiscalYears.breaksTheYearKey(
                new DataIntegrityViolationException(
                    "plain", new SQLException("without Hibernate", UNIQUE_VIOLATION))))
        .isTrue();
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Otro SQLState (FK, CHECK, NOT NULL) o un fallo sin SQL no son V-01")
  void anythingElseIsNotV01() {
    for (String sqlState : new String[] {"23503", "23514", "23502", "23000", "40001", null}) {
      assertThat(JpaFiscalYears.breaksTheYearKey(failure(sqlState))).as("%s", sqlState).isFalse();
    }
    assertThat(JpaFiscalYears.breaksTheYearKey(new DataIntegrityViolationException("no cause")))
        .isFalse();
  }
}
