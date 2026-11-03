package co.com.sapenlinea.liv.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A use case method that needs no permission of the user (decision of the project lead, 2026-11-03,
 * T14). It runs for any caller, also without a request: a listener, a job, the user's own data and
 * menu, or a session case protected by the scope of the service token. Every public method of a use
 * case carries either this or {@link RequiresPermission}; one without a mark is refused. The list
 * of open methods is closed and checked by the architecture tests, so adding one is a reviewed
 * change.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenUseCase {

  /** Why the method needs no permission, in Spanish, for the reviewer. */
  String value();
}
