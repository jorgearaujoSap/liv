package co.com.sapenlinea.liv.bff.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@link BffTest} with the usual issuer of the generic provider: the base of the flow tests. */
public abstract class BffFlowTest extends BffTest {

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    configure(registry, OidcProvider::issuer);
  }
}
