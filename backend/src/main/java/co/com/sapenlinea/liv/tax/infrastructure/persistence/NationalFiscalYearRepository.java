package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads the national table of fiscal years. */
interface NationalFiscalYearRepository extends JpaRepository<NationalFiscalYearEntity, Short> {

  List<NationalFiscalYearEntity> findAllByOrderByYearAsc();
}
