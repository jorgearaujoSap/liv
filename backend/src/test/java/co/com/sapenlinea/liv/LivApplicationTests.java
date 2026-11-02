package co.com.sapenlinea.liv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** El contexto arranca sin base de datos: la conexión se abre de forma perezosa. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:postgresql://localhost:1/sin-base",
      "spring.datasource.password=sin-uso"
    })
class LivApplicationTests {

  @Test
  @DisplayName("La aplicación arranca con todos los módulos")
  void contextLoads() {}
}
