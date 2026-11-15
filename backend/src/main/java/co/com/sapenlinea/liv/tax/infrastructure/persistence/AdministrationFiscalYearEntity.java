package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The row {@code tax.administration_fiscal_year}. It is only read and inserted, never updated by
 * dirty checking: {@code liv_app} can insert only {@code administration_id}, {@code fiscal_year}
 * and {@code created_by}, and update only {@code status}, {@code updated_by} and {@code updated_at}
 * (migrations V202611131000 and V202611141000).
 *
 * <ul>
 *   <li>{@code id}, {@code status}, {@code assessments_enabled} and {@code created_at} are left out
 *       of the {@code INSERT} ({@code insertable = false}) and Hibernate reads them back after it
 *       ({@code @Generated}): the database sets them ({@code uuidv7()} and the defaults).
 *   <li>{@code updated_by} and {@code updated_at} are left out too: PostgreSQL asks for the
 *       privilege of every column an {@code INSERT} names, even with a null value.
 *   <li>Every column is {@code updatable = false}: the state changes only with the explicit {@code
 *       UPDATE} of {@link AdministrationFiscalYearRepository#openPreparation}, so no {@code UPDATE}
 *       of Hibernate can touch a column without permission or collide with the row-level security.
 * </ul>
 */
@Entity
@Table(schema = "tax", name = "administration_fiscal_year")
class AdministrationFiscalYearEntity {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false)
  private UUID id;

  @Column(name = "administration_id", nullable = false, updatable = false)
  private UUID administrationId;

  @Column(name = "fiscal_year", nullable = false, updatable = false)
  private short fiscalYear;

  @Generated(event = EventType.INSERT)
  @Column(name = "status", insertable = false, updatable = false)
  private String status;

  @Generated(event = EventType.INSERT)
  @Column(name = "assessments_enabled", insertable = false, updatable = false)
  private boolean assessmentsEnabled;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_by", insertable = false, updatable = false)
  private UUID updatedBy;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;

  /** For JPA. */
  protected AdministrationFiscalYearEntity() {}

  AdministrationFiscalYearEntity(UUID administrationId, short fiscalYear, UUID createdBy) {
    this.administrationId = administrationId;
    this.fiscalYear = fiscalYear;
    this.createdBy = createdBy;
  }

  UUID id() {
    return id;
  }

  UUID administrationId() {
    return administrationId;
  }

  int fiscalYear() {
    return fiscalYear;
  }

  String status() {
    return status;
  }

  boolean assessmentsEnabled() {
    return assessmentsEnabled;
  }
}
