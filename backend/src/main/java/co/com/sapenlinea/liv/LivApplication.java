package co.com.sapenlinea.liv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the vehicle tax system. Each direct sub-package is an application module,
 * including {@code shared}: it is a normal module, not a {@code sharedModules} one, so every
 * module that uses it declares the arrow (ADR 0035). Spring Modulith only recognises packages that
 * contain classes.
 */
@SpringBootApplication
@Modulithic(systemName = "LIV")
public class LivApplication {

  public static void main(String[] args) {
    SpringApplication.run(LivApplication.class, args);
  }
}
