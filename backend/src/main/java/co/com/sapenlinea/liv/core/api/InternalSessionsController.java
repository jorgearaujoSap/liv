package co.com.sapenlinea.liv.core.api;

import co.com.sapenlinea.liv.core.api.contract.internal.InternalSessionsApi;
import co.com.sapenlinea.liv.core.api.contract.internal.model.EndSessionRequest;
import co.com.sapenlinea.liv.core.api.contract.internal.model.StartSessionRequest;
import co.com.sapenlinea.liv.core.api.contract.internal.model.StartSessionResponse;
import co.com.sapenlinea.liv.core.application.EndSession;
import co.com.sapenlinea.liv.core.application.StartSession;
import co.com.sapenlinea.liv.core.application.StartSession.StartedSession;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.TokenUser;
import co.com.sapenlinea.liv.platform.VerifiedUserToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session start and end for the BFF (core-internal.yaml, ADR 0025). The security filters already
 * checked the service token and, on start, the user token of {@code X-User-Token}.
 */
@RestController
class InternalSessionsController implements InternalSessionsApi {

  private final StartSession startSession;
  private final EndSession endSession;

  InternalSessionsController(StartSession startSession, EndSession endSession) {
    this.startSession = startSession;
    this.endSession = endSession;
  }

  /** The header is read from the validated token the filter left, not from its raw value. */
  @Override
  public ResponseEntity<StartSessionResponse> startSession(
      String xUserToken, StartSessionRequest request) {
    TokenUser user =
        VerifiedUserToken.current()
            .orElseThrow(() ->
                new AuthenticationRejectedException(
                    ErrorCodes.NOT_AUTHENTICATED, "No validated user token"));
    StartedSession session = startSession.start(user, request.getSid());
    return ResponseEntity.status(session.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(new StartSessionResponse(session.userId()));
  }

  @Override
  public ResponseEntity<Void> endSession(String sid, EndSessionRequest request) {
    endSession.end(sid, request.getIssuer(), request.getSubject(), request.getReason().getValue());
    return ResponseEntity.noContent().build();
  }
}
