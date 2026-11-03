package co.com.sapenlinea.liv.platform.authorization;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Every public method of a use case — a bean of a module's {@code application} package — goes
 * through the permission check, marked or not, inherited or not: a method without a mark is then
 * refused (deny by default, decision of the project lead, T14). The methods of {@link Object} are
 * left alone, so logging or comparing a service does not fail.
 */
final class UseCasePointcut extends StaticMethodMatcherPointcut {

  private static final Pattern USE_CASES =
      Pattern.compile("co\\.com\\.sapenlinea\\.liv\\.[a-z]+\\.application(\\..+)?");

  @Override
  public boolean matches(Method method, Class<?> targetClass) {
    Class<?> type = ClassUtils.getUserClass(targetClass);
    return isUseCase(type)
        && Modifier.isPublic(method.getModifiers())
        && method.getDeclaringClass() != Object.class
        && !ReflectionUtils.isEqualsMethod(method)
        && !ReflectionUtils.isHashCodeMethod(method)
        && !ReflectionUtils.isToStringMethod(method);
  }

  private static boolean isUseCase(Class<?> type) {
    return USE_CASES.matcher(type.getPackageName()).matches()
        && AnnotatedElementUtils.hasAnnotation(type, Component.class);
  }
}
