package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Authorization lives in one place (plan, step 7, and decisions of the project lead of 2026-11-03,
 * T14): every public method of a use case — a {@code @Service} of {@code application} — carries
 * {@code @RequiresPermission} or {@code @OpenUseCase}, and the proxy that checks them must be able
 * to intercept it. At runtime a method without a mark is refused; these rules keep the code from
 * reaching that point. They take the packages of the use cases so that AuthorizationRulesTest can
 * prove them with fixtures.
 */
final class AuthorizationRules {

  /** Where the use cases of the modules live. */
  static final String USE_CASES = "..liv.*.application..";

  /** The only use cases open to any caller (decision of the project lead, T14), by method. */
  static final Set<String> OPEN_USE_CASES =
      Set.of(
          "co.com.sapenlinea.liv.core.application.GetCurrentUser.get",
          "co.com.sapenlinea.liv.core.application.StartSession.start",
          "co.com.sapenlinea.liv.core.application.EndSession.end");

  private AuthorizationRules() {}

  /**
   * A bean of {@code application} is a {@code @Service} the proxy can intercept: not final and
   * without a superclass, whose public methods would escape the check of the class's own marks.
   */
  static ArchRule useCasesAreInterceptableServices(String useCases) {
    return classes()
        .that()
        .resideInAPackage(useCases)
        .and()
        .areMetaAnnotatedWith(Component.class)
        .should()
        .beAnnotatedWith(Service.class)
        .andShould()
        .notHaveModifier(JavaModifier.FINAL)
        .andShould(extendOnlyObject())
        .because("the permission check is a proxy on the services of application (001-16)");
  }

  /** Each public method of a use case declares its permission, or that it is open (001-16). */
  static ArchRule useCaseMethodsAreMarked(String useCases) {
    return methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat()
        .resideInAPackage(useCases)
        .and()
        .areDeclaredInClassesThat()
        .areMetaAnnotatedWith(Component.class)
        .should(haveExactlyOneMarkAndBeInterceptable())
        .because("a hidden option is still reachable by URL; the use case checks (001-16)");
  }

  /** The marks go only where the proxy checks them: public methods of use cases. */
  static ArchRule marksOnlyOnUseCases(String useCases) {
    return methods()
        .that()
        .areAnnotatedWith(RequiresPermission.class)
        .or()
        .areAnnotatedWith(OpenUseCase.class)
        .should()
        .bePublic()
        .andShould()
        .notHaveModifier(JavaModifier.STATIC)
        .andShould()
        .notHaveModifier(JavaModifier.FINAL)
        .andShould()
        .beDeclaredInClassesThat()
        .resideInAPackage(useCases)
        .andShould()
        .beDeclaredInClassesThat()
        .areAnnotatedWith(Service.class)
        .because("authorization has one place: the services of application (plan, step 7)")
        .allowEmptyShould(true);
  }

  /** A call through {@code this} skips the proxy, and with it the permission of the callee. */
  static ArchRule noSelfCallsToGuardedMethods(String useCases) {
    return classes()
        .that()
        .resideInAPackage(useCases)
        .and()
        .areAnnotatedWith(Service.class)
        .should(notCallTheirOwnGuardedMethods())
        .because("a call through this is not checked (001-16, segregation of duties)")
        .allowEmptyShould(true);
  }

  /** Spring's own method security is off; its annotations would compile and check nothing. */
  static ArchRule noSpringMethodSecurityAnnotations() {
    return noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.security.access.prepost..",
            "org.springframework.security.access.annotation..",
            "jakarta.annotation.security..")
        .because("only @RequiresPermission and @OpenUseCase are checked (plan, step 7)");
  }

  private static ArchCondition<JavaClass> extendOnlyObject() {
    return new ArchCondition<>("extend no class but Object") {
      @Override
      public void check(JavaClass useCase, ConditionEvents events) {
        boolean plain =
            useCase.getRawSuperclass().map(s -> s.isEquivalentTo(Object.class)).orElse(true);
        events.add(
            new SimpleConditionEvent(
                useCase, plain, useCase.getName() + " inherits public methods from a superclass"));
      }
    };
  }

  private static ArchCondition<JavaMethod> haveExactlyOneMarkAndBeInterceptable() {
    return new ArchCondition<>("have exactly one of @RequiresPermission or @OpenUseCase") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean guarded = method.isAnnotatedWith(RequiresPermission.class);
        boolean open = method.isAnnotatedWith(OpenUseCase.class);
        Set<JavaModifier> modifiers = method.getModifiers();
        boolean interceptable =
            !modifiers.contains(JavaModifier.STATIC) && !modifiers.contains(JavaModifier.FINAL);
        events.add(
            new SimpleConditionEvent(
                method,
                guarded != open && interceptable,
                method.getFullName()
                    + (interceptable
                        ? " needs exactly one of @RequiresPermission or @OpenUseCase"
                        : " is static or final: the proxy cannot check it")));
      }
    };
  }

  private static ArchCondition<JavaClass> notCallTheirOwnGuardedMethods() {
    return new ArchCondition<>("not call their own @RequiresPermission methods") {
      @Override
      public void check(JavaClass useCase, ConditionEvents events) {
        for (JavaMethodCall call : useCase.getMethodCallsFromSelf()) {
          boolean guardedSelfCall =
              call.getTargetOwner().equals(useCase)
                  && call.getTarget()
                      .resolveMember()
                      .map(target -> target.isAnnotatedWith(RequiresPermission.class))
                      .orElse(false);
          if (guardedSelfCall) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }
}
