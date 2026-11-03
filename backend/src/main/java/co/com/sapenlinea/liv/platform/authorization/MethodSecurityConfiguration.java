package co.com.sapenlinea.liv.platform.authorization;

import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method security with LIV's own annotation only (plan of feature 001, step 7, «Autorización»):
 * {@link RequiresPermission} through a Spring AOP proxy, without AspectJ; {@code @PreAuthorize} and
 * its kin stay off. The check runs before any transaction of the use case: a refused call opens
 * none. A denial raises an {@code AccessDeniedException}, answered with 403 and the code 001-16.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity(prePostEnabled = false)
class MethodSecurityConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static Advisor requiresPermissionAdvisor(ObjectProvider<PermissionCatalog> catalog) {
    return new AuthorizationManagerBeforeMethodInterceptor(
        AnnotationMatchingPointcut.forMethodAnnotation(RequiresPermission.class),
        new RequiresPermissionAuthorizationManager(catalog));
  }
}
