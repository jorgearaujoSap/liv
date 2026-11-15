package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads, inserts and opens the fiscal years of the administrations. */
interface AdministrationFiscalYearRepository
    extends JpaRepository<AdministrationFiscalYearEntity, UUID> {

  List<AdministrationFiscalYearEntity> findByAdministrationIdOrderByFiscalYearDesc(
      UUID administrationId);

  Optional<AdministrationFiscalYearEntity> findByAdministrationIdAndFiscalYear(
      UUID administrationId, short fiscalYear);

  /**
   * Takes the advisory lock of the transaction for the administration, waiting if another
   * transaction holds it; it is released when the transaction ends. The {@code SELECT 1} form
   * avoids reading the {@code void} column of the function.
   */
  @Query(
      value = "SELECT 1 FROM pg_advisory_xact_lock(:space, hashtext(:administrationId))",
      nativeQuery = true)
  int lockForCreation(
      @Param("space") int space, @Param("administrationId") String administrationId);

  /**
   * The opening of the preparation, explicit: it moves the year from {@code PLANEADA} to {@code
   * EN_PREPARACION} only if it is still {@code PLANEADA}, and returns how many rows it changed (0
   * when another request got there first, or when the row-level security hides the row). The time
   * is the one of the database, so it can never be in the future. Hibernate flushes before it and
   * clears its persistence context after it, so the next read comes from the database.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE tax.administration_fiscal_year
             SET status = 'EN_PREPARACION', updated_by = :userId, updated_at = now()
           WHERE id = :id AND status = 'PLANEADA'
          """,
      nativeQuery = true)
  int openPreparation(@Param("id") UUID id, @Param("userId") UUID userId);
}
