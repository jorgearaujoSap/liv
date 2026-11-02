package co.com.sapenlinea.liv.platform.web;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Problem Details (RFC 9457) for authentication and authorization, with the code of the criterion
 * (docs/arquitectura/01 §10). They say nothing about the token or the user (001-14: "sin datos");
 * the reason stays in the exception, never in the response.
 */
@RestControllerAdvice
class SecurityProblemHandler {

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException exception) {
    String code =
        exception instanceof AuthenticationRejectedException rejected
            ? rejected.code()
            : ErrorCodes.NOT_AUTHENTICATED;
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(problem(HttpStatus.UNAUTHORIZED, "No autenticado", code));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problem(HttpStatus.FORBIDDEN, "Sin permiso", ErrorCodes.FORBIDDEN));
  }

  private static ProblemDetail problem(HttpStatus status, String title, String code) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
