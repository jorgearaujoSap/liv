package co.com.sapenlinea.liv.platform.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock of the application, in UTC. Use cases get it injected instead of reading the system
 * time, so the tests can fix it (rule of the backend, «Reloj»).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
