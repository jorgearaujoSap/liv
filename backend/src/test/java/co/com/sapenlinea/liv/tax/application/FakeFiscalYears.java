package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

  private final List<RegisteredFiscalYear> rows = new ArrayList<>();
  private final List<UUID> lockedAdministrations = new ArrayList<>();
  private final List<String> calls = new ArrayList<>();

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
  RegisteredFiscalYear given(UUID administration, int year, FiscalYearStatus status) {
    RegisteredFiscalYear stored =
        new RegisteredFiscalYear(
            UUID.randomUUID(),
            AdministrationFiscalYear.restore(administration, year, status, false),
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 12, 31));
    rows.add(stored);
    return stored;
  }

  /** The administrations {@code lockForCreation} was called with, in order. */
  List<UUID> lockedAdministrations() {
    return List.copyOf(lockedAdministrations);
  }

  /** The reads and writes the use case made through the port, in order. */
  List<String> calls() {
    return List.copyOf(calls);
  }

  @Override
  public void lockForCreation(UUID administrationId) {
    calls.add("lock");
    lockedAdministrations.add(administrationId);
  }

  @Override
  public List<RegisteredFiscalYear> findAll(UUID administrationId) {
    calls.add("findAll");
    return rowsOf(administrationId);
  }

  private List<RegisteredFiscalYear> rowsOf(UUID administrationId) {
    return rows.stream()
        .filter(row -> row.fiscalYear().administrationId().equals(administrationId))
        .sorted(
            Comparator.comparingInt((RegisteredFiscalYear row) -> row.fiscalYear().year())
                .reversed())
        .toList();
  }

  @Override
  public Optional<RegisteredFiscalYear> find(UUID administrationId, int year) {
    calls.add("find");
    return rowsOf(administrationId).stream()
        .filter(row -> row.fiscalYear().year() == year)
        .findFirst();
  }

  @Override
  public List<NationalFiscalYear> nationalYears() {
    calls.add("national");
    return nationalTable();
  }

  @Override
  public RegisteredFiscalYear create(AdministrationFiscalYear fiscalYear, UUID createdBy) {
    // It does not enforce V-01, as a UNIQUE would: the use case must have decided it before.
    calls.add("create");
    return given(fiscalYear.administrationId(), fiscalYear.year(), fiscalYear.status());
  }

  @Override
  public RegisteredFiscalYear persistOpened(RegisteredFiscalYear current, UUID openedBy) {
    // It does not validate the state, as the UPDATE of the database would (0 rows): the use case
    // must have decided V-06 before. It only records the call and changes the state.
    calls.add("persistOpened");
    rows.remove(current);
    RegisteredFiscalYear opened =
        new RegisteredFiscalYear(
            current.id(),
            AdministrationFiscalYear.restore(
                current.fiscalYear().administrationId(),
                current.fiscalYear().year(),
                FiscalYearStatus.EN_PREPARACION,
                false),
            current.startsOn(),
            current.endsOn());
    rows.add(opened);
    return opened;
  }
}
