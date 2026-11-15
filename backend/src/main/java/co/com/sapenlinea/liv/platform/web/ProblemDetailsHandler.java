package co.com.sapenlinea.liv.platform.web;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.RequestRefusedException;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error of the API as Problem Details (RFC 9457) with a {@code code} (docs/arquitectura/01
 * §10), and never internal details: the technical detail goes to the log, where the line carries
 * the correlation id (doc. 02 §8). Rejections say nothing about the token or the user (001-14).
 *
 * <p>Codes: the criterion for security rejections, {@code SYS-<status>} for technical errors (the
 * {@code SYS-} constants of {@link ErrorCodes}, or built from the status for the standard Spring
 * MVC errors: 400, 404, 405, 415…, which keep their status).
 *
 * <p>The business errors of {@code shared} (ADR 0035) are answered here without {@code platform}
 * knowing the modules that throw them: a {@link RuleViolation} is a 409 or a 422 with the code of
 * the rule and its details as extensions, and a {@link NotFound} is the 404 {@code SYS-404} with no
 * detail, so what does not exist and what belongs to another administration look the same (CA-12).
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsHandler.class);

  /** A bad token that reaches a controller is still a rejection, not an unavailable provider. */
  @ExceptionHandler({AuthenticationException.class, BadJwtException.class})
  ResponseEntity<ProblemDetail> unauthenticated(Exception exception) {
    String code =
        exception instanceof AuthenticationRejectedException rejected
            ? rejected.code()
            : ErrorCodes.NOT_AUTHENTICATED;
    LOG.warn("Request rejected with {}: {}", code, exception.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(problem(HttpStatus.UNAUTHORIZED, "No autenticado", code));
  }

  /** A refusal with the code of its criterion, such as a session start (001-05, 001-17). */
  @ExceptionHandler(RequestRefusedException.class)
  ResponseEntity<ProblemDetail> refused(RequestRefusedException exception) {
    LOG.warn("Request refused with {}: {}", exception.code(), exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problem(HttpStatus.FORBIDDEN, "Rechazado", exception.code()));
  }

  /** A rule refused the action: an expected answer, not a failure, so it is not logged as one. */
  @ExceptionHandler(RuleViolation.class)
  ResponseEntity<ProblemDetail> ruleViolation(RuleViolation violation) {
    // Only the names of the details: their values may be personal data in later rules.
    LOG.info("Rule {} refused the request: {}", violation.code(), violation.details().keySet());
    HttpStatus status =
        violation.type() == RuleViolation.Type.CONFLICT
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_CONTENT;
    ProblemDetail problem = problem(status, "Regla de negocio", violation.code());
    violation.details().forEach(problem::setProperty);
    return ResponseEntity.status(status).body(problem);
  }

  /** The same body whatever the reason: the log has it, the answer does not (CA-12). */
  @ExceptionHandler(NotFound.class)
  ResponseEntity<ProblemDetail> notFound(NotFound exception) {
    LOG.info("{}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(HttpStatus.NOT_FOUND, "No encontrado", ErrorCodes.NOT_FOUND));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception) {
    LOG.warn("Request rejected with {}: {}", ErrorCodes.FORBIDDEN, exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problem(HttpStatus.FORBIDDEN, "Sin permiso", ErrorCodes.FORBIDDEN));
  }

  /**
   * The database or the identity provider does not answer. Other database errors (integrity, RLS,
   * SQL) are not an outage: they are a 500, so a write into another administration is not retried.
   * Spring Security reports a provider that cannot be reached as {@link
   * AuthenticationServiceException} on {@code /api}, and the decoder of {@code X-User-Token} as
   * {@link JwtDecoderInitializationException} or a plain {@link JwtException} (a bad token is a
   * {@link BadJwtException}, handled above).
   */
  @ExceptionHandler({
    DataAccessResourceFailureException.class,
    TransientDataAccessException.class,
    CannotCreateTransactionException.class,
    AuthenticationServiceException.class,
    JwtDecoderInitializationException.class,
    JwtException.class
  })
  ResponseEntity<ProblemDetail> unavailable(Exception exception) {
    LOG.error("Dependency unavailable", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio no disponible",
                ErrorCodes.SERVICE_UNAVAILABLE));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    LOG.error("Unexpected error", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ErrorCodes.INTERNAL_ERROR));
  }

  /** The standard Spring MVC errors that are the server's fault go to the log, like ours. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) {
      LOG.error("Request failed with {}", status.value(), exception);
    }
    return super.handleExceptionInternal(exception, body, headers, status, request);
  }

  /** The standard Spring MVC errors: same status, without detail, with the technical code. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      problem.setDetail(null);
      problem.setProperty("code", "SYS-" + status.value());
    }
    return super.createResponseEntity(body, headers, status, request);
  }

  private static ProblemDetail problem(HttpStatus status, String title, String code) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
