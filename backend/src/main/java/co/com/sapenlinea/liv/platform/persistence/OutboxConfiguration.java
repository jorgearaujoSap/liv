package co.com.sapenlinea.liv.platform.persistence;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The JDBC outbox of Spring Modulith without touching the database at startup, so the API starts
 * even if the database is down and the readiness probe tells (plan, ajustes de T12).
 *
 * <p>Spring Modulith asks the database for its type when it creates its repository. LIV only runs
 * on PostgreSQL, so the bean of that type is given here instead of asked. The type of Spring
 * Modulith is not public: it is looked up by name, and if a version renames it or its bean, the
 * application fails at startup, in every test, not later.
 */
@Configuration(proxyBeanMethods = false)
class OutboxConfiguration {

  private static final String DATABASE_TYPE_BEAN = "databaseType";
  private static final String DATABASE_TYPE =
      "org.springframework.modulith.events.jdbc.DatabaseType";

  @Bean
  static BeanFactoryPostProcessor postgresOutbox() {
    return beanFactory -> {
      if (!beanFactory.containsBeanDefinition(DATABASE_TYPE_BEAN)
          || !(beanFactory.getBeanDefinition(DATABASE_TYPE_BEAN)
              instanceof AbstractBeanDefinition definition)) {
        // A version of Spring Modulith that renamed its bean would read the database at startup.
        throw new IllegalStateException(
            "Spring Modulith changed: no bean '" + DATABASE_TYPE_BEAN + "' to set to PostgreSQL");
      }
      definition.setInstanceSupplier(OutboxConfiguration::postgres);
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object postgres() {
    try {
      Class type = Class.forName(DATABASE_TYPE, false, OutboxConfiguration.class.getClassLoader());
      return Enum.valueOf(type, "POSTGRES");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Spring Modulith changed: " + DATABASE_TYPE + " not found", e);
    }
  }
}
