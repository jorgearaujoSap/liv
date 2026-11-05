package co.com.sapenlinea.liv.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** BFF de LIV (ADR 0010): sesión OIDC en el servidor y puerta de entrada del navegador a la API. */
@SpringBootApplication
public class LivBffApplication {

  public static void main(String[] args) {
    SpringApplication.run(LivBffApplication.class, args);
  }
}
