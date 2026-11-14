package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * In-memory double of the {@link FiscalYears} port for the unit tests of the use cases. The
 * national table is the one of the migration (1999 to 2100).
 */
final class FakeFiscalYears implements FiscalYears {

  static final UUID ADMINISTRATION = UUID.fromString("10000000-0000-0000-0000-00000000000a");
  static final UUID OTHER_ADMINISTRATION = UUID.fromString("10000000-0000-0000-0000-00000000000b");
  static final UUID USER = UUID.fromString("20000000-0000-0000-0000-00000000000a");

  private final List<Stored> rows = new ArrayList<>();
  private int locks;

  static List<NationalFiscalYear> nationalTable() {
    return IntStream.rangeClosed(1999, 2100)
        .mapToObj(year ->
            new NationalFiscalYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
        .toList();
  }

  /** A user of {@link #ADMINISTRATION}. */
  static CurrentUser user() {
    return new CurrentUser(USER, ADMINISTRATION, "https://idp.test", "subject", Set.of("ROLE"));
  }

  /** A catalog where every role of the token has exactly these permissions. */
  static PermissionCatalog catalogWith(String... permissions) {
    Set<String> granted = Set.of(permissions);
    return new PermissionCatalog() {
      @Override
      public Set<String> knownRoles(Set<String> roles) {
        return roles;
      }

      @Override
      public Set<String> permissionsOf(Set<String> roles) {
        return granted;
      }
    };
  }

  /** Adds an existing fiscal year of the administration, in the given state. */
  Stored given(UUID administration, int year, FiscalYearStatus status) {
    Stored stored =
        new Stored(
            UUID.randomUUID(),
            AdministrationFiscalYear.restore(administration, year, status, false),
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 12, 31));
    rows.add(stored);
    return stored;
  }

  int locks() {
    return locks;
  }

  @Override
  public void lockForCreation(UUID administrationId) {
    locks++;
  }

  @Override
  public List<Stored> findAll(UUID administrationId) {
    return rows.stream()
        .filter(row -> row.fiscalYear().administrationId().equals(administrationId))
        .sorted(Comparator.comparingInt((Stored row) -> row.fiscalYear().year()).reversed())
        .toList();
  }

  @Override
  public Optional<Stored> find(UUID administrationId, int year) {
    return findAll(administrationId).stream()
        .filter(row -> row.fiscalYear().year() == year)
        .findFirst();
  }

  @Override
  public List<NationalFiscalYear> nationalYears() {
    return nationalTable();
  }

  @Override
  public Stored create(AdministrationFiscalYear fiscalYear, UUID createdBy) {
    if (find(fiscalYear.administrationId(), fiscalYear.year()).isPresent()) {
      throw new RuleViolation(
          "V-01", RuleViolation.Type.CONFLICT, Map.of("year", fiscalYear.year()));
    }
    return given(fiscalYear.administrationId(), fiscalYear.year(), fiscalYear.status());
  }

  @Override
  public Stored persistOpened(Stored current, UUID openedBy) {
    rows.remove(current);
    Stored opened =
        new Stored(
            current.id(),
            current.fiscalYear().openPreparation(),
            current.startsOn(),
            current.endsOn());
    rows.add(opened);
    return opened;
  }
}
