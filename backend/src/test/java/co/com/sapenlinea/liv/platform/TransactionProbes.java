package co.com.sapenlinea.liv.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableAsync;

/** The probes of {@link TransactionContextTest}, which exist only in the tests. */
@TestConfiguration
@EnableAsync
class TransactionProbes {

  @Bean
  TransactionContextTest.RlsProbe rlsProbe(
      JdbcClient jdbc, TransactionContextTest.NewTransactionProbe inner) {
    return new TransactionContextTest.RlsProbe(jdbc, inner);
  }

  @Bean
  TransactionContextTest.NewTransactionProbe newTransactionProbe(JdbcClient jdbc) {
    return new TransactionContextTest.NewTransactionProbe(jdbc);
  }

  @Bean
  TransactionContextTest.Publisher publisher(ApplicationEventPublisher events) {
    return new TransactionContextTest.Publisher(events);
  }

  @Bean
  TransactionContextTest.Listener listener(TransactionContextTest.RlsProbe probe) {
    return new TransactionContextTest.Listener(probe);
  }
}
