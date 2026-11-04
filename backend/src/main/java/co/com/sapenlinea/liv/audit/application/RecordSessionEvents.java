package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionEnded;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.core.UserProvisioned;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import org.springframework.stereotype.Service;

/** The audit of the sessions of LIV (001-18). Pending: T15. */
@Service
public class RecordSessionEvents {

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición (T15)")
  public void started(SessionStarted event) {
    throw new UnsupportedOperationException("T15");
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición (T15)")
  public void ended(SessionEnded event) {
    throw new UnsupportedOperationException("T15");
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición (T15)")
  public void denied(SessionDenied event) {
    throw new UnsupportedOperationException("T15");
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición (T15)")
  public void provisioned(UserProvisioned event) {
    throw new UnsupportedOperationException("T15");
  }
}
