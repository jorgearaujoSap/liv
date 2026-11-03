package co.com.sapenlinea.liv.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The permission a use case requires (001-16, plan step 7): it goes on the public methods of the
 * services of {@code application}. Before the method runs, the user of the request must have it in
 * LIV's catalog through one of the roles of its token; otherwise the call is refused with 403 and
 * the method does not run. A hidden menu option is not enough: the option is still reachable by
 * URL.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

  /** The permission, {@code <module>.<resource>.<action>}, for example {@code core.user.read}. */
  String value();
}
