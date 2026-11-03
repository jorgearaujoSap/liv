package co.com.sapenlinea.liv.platform.persistence;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

/**
 * Sets the RLS context at the start of every transaction (doc. 02 §5.1, plan step 7), including
 * {@code REQUIRES_NEW}: {@code app.administration_id} gets the administration of {@link
 * AdministrationContext}, with local scope, so it ends with the transaction and nothing stays on
 * the pooled connection. Without a context nothing is set and the policies return no rows.
 *
 * <p>It runs inside {@code JpaTransactionManager.doBegin}: if setting the context fails, the
 * manager closes the half-begun transaction like any failed begin.
 */
class AdministrationJpaDialect extends HibernateJpaDialect {

  private static final String SET_CONTEXT = "SELECT set_config('app.administration_id', ?, true)";

  @Override
  public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
      throws PersistenceException, SQLException {
    Object transactionData = super.beginTransaction(entityManager, definition);
    Optional<UUID> administration = AdministrationContext.current();
    if (administration.isPresent()) {
      String administrationId = administration.get().toString();
      entityManager
          .unwrap(Session.class)
          .doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
              statement.setString(1, administrationId);
              statement.execute();
            }
          });
    }
    return transactionData;
  }
}
