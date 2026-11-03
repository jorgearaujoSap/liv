package co.com.sapenlinea.liv.platform.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The transaction manager of the application (plan step 7): Spring Boot's {@link
 * JpaTransactionManager}, extended so every transaction starts with the RLS context. It replaces
 * the one Spring Boot would create and keeps its customizations ({@code spring.transaction.*}).
 */
@Configuration(proxyBeanMethods = false)
class TransactionConfiguration {

  @Bean
  PlatformTransactionManager transactionManager(
      ObjectProvider<TransactionManagerCustomizers> customizers) {
    AdministrationTransactionManager manager = new AdministrationTransactionManager();
    customizers.ifAvailable(all -> all.customize(manager));
    return manager;
  }

  /**
   * {@code JpaTransactionManager.afterPropertiesSet} copies the dialect of the entity manager
   * factory into the manager, which would replace any dialect set before. The RLS dialect is set
   * after it, so every transaction begins through it.
   */
  static class AdministrationTransactionManager extends JpaTransactionManager {

    @Override
    public void afterPropertiesSet() {
      super.afterPropertiesSet();
      setJpaDialect(new AdministrationJpaDialect());
    }
  }
}
