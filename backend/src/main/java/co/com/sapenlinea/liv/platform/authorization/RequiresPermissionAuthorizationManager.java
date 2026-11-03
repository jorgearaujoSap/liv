package co.com.sapenlinea.liv.platform.authorization;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

/**
 * Checks {@link RequiresPermission} before the use case runs (001-16). The user is the one the
 * security filter resolved in the LIV registry, and its permissions are those LIV gives to the
 * roles of its token that LIV knows; Spring's {@link Authentication} is not used. Without a user of
 * a request (a listener, a job) there is nobody to authorize, and the call is refused.
 */
final class RequiresPermissionAuthorizationManager
    implements AuthorizationManager<MethodInvocation> {

  private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);
  private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);

  /** Looked up on each check: the catalog reads the database, created after the advisors. */
  private final ObjectProvider<PermissionCatalog> catalog;

  RequiresPermissionAuthorizationManager(ObjectProvider<PermissionCatalog> catalog) {
    this.catalog = catalog;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
    // The user comes from the LIV registry (CurrentUser), not from Spring's Authentication.
    Optional<CurrentUser> user = CurrentUser.current();
    RequiresPermission required = requiredBy(invocation);
    if (user.isEmpty() || required == null) {
      return DENIED;
    }
    boolean granted =
        catalog.getObject().permissionsOf(user.get().roles()).contains(required.value());
    return granted ? GRANTED : DENIED;
  }

  private static RequiresPermission requiredBy(MethodInvocation invocation) {
    return AnnotatedElementUtils.findMergedAnnotation(
        useCaseMethod(invocation), RequiresPermission.class);
  }

  /** The method of the use case class, not of the proxy. */
  private static Method useCaseMethod(MethodInvocation invocation) {
    Object target = invocation.getThis();
    return target == null
        ? invocation.getMethod()
        : AopUtils.getMostSpecificMethod(invocation.getMethod(), AopUtils.getTargetClass(target));
  }
}
