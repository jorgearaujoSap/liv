package co.com.sapenlinea.liv.platform.authorization;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Checks a use case method before it runs (001-16), denying by default:
 *
 * <ul>
 *   <li>{@link RequiresPermission}: the user of the request must have the permission through one of
 *       the roles of its token that LIV knows. Without a user of a request there is nobody to
 *       authorize, and the call is refused;
 *   <li>{@link OpenUseCase}: it runs for any caller, also a listener or a job;
 *   <li>neither: refused to everyone, so a forgotten mark never opens a use case.
 * </ul>
 *
 * <p>The user is the one the security filter resolved in the LIV registry; Spring's {@link
 * Authentication} is not used. Its permissions are read once per request.
 */
final class RequiresPermissionAuthorizationManager
    implements AuthorizationManager<MethodInvocation> {

  private static final Logger LOG =
      LoggerFactory.getLogger(RequiresPermissionAuthorizationManager.class);

  private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);
  private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);

  /** Request attribute with the permissions of the user, read once per request. */
  private static final String PERMISSIONS =
      RequiresPermissionAuthorizationManager.class.getName() + ".permissions";

  /** Looked up on each check: the catalog reads the database, created after the advisors. */
  private final ObjectProvider<PermissionCatalog> catalog;

  RequiresPermissionAuthorizationManager(ObjectProvider<PermissionCatalog> catalog) {
    this.catalog = catalog;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
    Method method = useCaseMethod(invocation);
    RequiresPermission required =
        AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
    if (required == null) {
      if (AnnotatedElementUtils.hasAnnotation(method, OpenUseCase.class)) {
        return GRANTED;
      }
      LOG.warn(
          "Use case method without @RequiresPermission or @OpenUseCase refused: {}", name(method));
      return DENIED;
    }
    // The user comes from the LIV registry (CurrentUser), not from Spring's Authentication.
    Optional<CurrentUser> user = CurrentUser.current();
    if (user.isEmpty()) {
      LOG.warn("Use case refused outside a request, without a user: {}", name(method));
      return DENIED;
    }
    return permissionsForRequest(user.get()).contains(required.value()) ? GRANTED : DENIED;
  }

  /** The permissions of the user, cached in the request it belongs to. */
  private Set<String> permissionsForRequest(CurrentUser user) {
    RequestAttributes request = RequestContextHolder.currentRequestAttributes();
    if (request.getAttribute(PERMISSIONS, RequestAttributes.SCOPE_REQUEST)
        instanceof CachedPermissions cached) {
      return cached.permissions();
    }
    Set<String> permissions = catalog.getObject().permissionsOf(user.roles());
    request.setAttribute(
        PERMISSIONS, new CachedPermissions(permissions), RequestAttributes.SCOPE_REQUEST);
    return permissions;
  }

  /** The method of the use case class, not of the proxy. */
  private static Method useCaseMethod(MethodInvocation invocation) {
    Object target = invocation.getThis();
    return target == null
        ? invocation.getMethod()
        : AopUtils.getMostSpecificMethod(invocation.getMethod(), AopUtils.getTargetClass(target));
  }

  private static String name(Method method) {
    return method.getDeclaringClass().getName() + "." + method.getName();
  }

  /** The permissions of the user, kept only for the request they belong to. */
  private record CachedPermissions(Set<String> permissions) {}
}
