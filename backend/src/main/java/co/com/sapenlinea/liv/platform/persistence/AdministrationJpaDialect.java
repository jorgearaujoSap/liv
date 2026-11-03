package co.com.sapenlinea.liv.platform.persistence;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

/**
 * Sets the RLS context at the start of every transaction (doc. 02 §5.1, plan step 7), including
 * {@code REQUIRES_NEW}, with local scope, so it ends with the transaction:
 *
 * <ul>
 *   <li>{@code app.administration_id} gets the administration of {@link AdministrationContext}, or
 *       empty without one (no rows), whatever the connection, the role or the database had;
 *   <li>{@code app.platform_job} starts empty; only a platform job sets it, after the begin.
 * </ul>
 *
 * <p>It runs inside {@code JpaTransactionManager.doBegin}: if setting the context fails, the
 * manager closes the half-begun transaction like any failed begin.
 */
class AdministrationJpaDialect extends HibernateJpaDialect {

  /** The administration (or empty), and the platform mark always cleared. */
  private static final String SET_CONTEXT =
      "SELECT set_config('app.administration_id', ?, true),"
          + " set_config('app.platform_job', '', true)";

  @Override
  public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
      throws PersistenceException, SQLException {
    Object transactionData = super.beginTransaction(entityManager, definition);
    String administrationId = AdministrationContext.current().map(UUID::toString).orElse("");
    entityManager
        .unwrap(Session.class)
        .doWork(connection -> {
          try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            statement.setString(1, administrationId);
            statement.execute();
          }
        });
    return transactionData;
  }
}
