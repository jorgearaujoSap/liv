package co.com.sapenlinea.liv.platform.application;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Use cases that exist only in the tests (001-16), in an {@code application} package like the real
 * ones, so the deny-by-default check applies to them. {@code run} requires {@code core.user.read},
 * which ADMIN_SISTEMA and AUDITOR have and CONSULTA does not (plan, «Permisos»).
 */
@Service
public class PermissionProbe extends ProbeBase {

  private final AtomicInteger runs = new AtomicInteger();

  @RequiresPermission("core.user.read")
  public String run() {
    return ran();
  }

  /** Without a mark: refused to everyone, whatever its permissions. */
  public String unmarked() {
    return ran();
  }

  @OpenUseCase("Prueba: un caso abierto corre también sin usuario")
  public String open() {
    return ran();
  }

  /** How many times a use case of the probe got past the check. */
  @OpenUseCase("Prueba: solo lee el contador del probe")
  public int runs() {
    return runs.get();
  }

  private String ran() {
    runs.incrementAndGet();
    return "ejecutado";
  }
}

/** A public method that the use case inherits without a mark of its own. */
class ProbeBase {

  public String inherited() {
    return "heredado";
  }
}
