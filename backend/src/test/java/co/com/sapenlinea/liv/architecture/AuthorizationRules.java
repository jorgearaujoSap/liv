package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

/**
 * Authorization lives in one place (plan, step 7, and decisions of the project lead of 2026-11-03,
 * T14): every public method of a use case — a {@code @Service} of {@code application} — carries
 * {@code @RequiresPermission} or {@code @OpenUseCase}, and the proxy that checks them must be able
 * to intercept it. At runtime a method without a mark is refused; these rules keep the code from
 * reaching that point, and close the ways around the proxy. They take the packages of the use cases
 * so that AuthorizationRulesTest can prove them with fixtures.
 */
final class AuthorizationRules {

  /** Where the use cases of the modules live. */
  // Same criterion as UseCasePointcut, which applies the check at runtime with a regular
  // expression: co.com.sapenlinea.liv.<module>.application and its subpackages.
  static final String USE_CASES = "..liv.*.application..";

  /**
   * The only use cases open to any caller (decision of the project lead, T14), by full signature: a
   * new overload is a new entry.
   */
  static final Set<String> OPEN_USE_CASES =
      Set.of(
          "co.com.sapenlinea.liv.core.application.GetCurrentUser.get("
              + "co.com.sapenlinea.liv.platform.CurrentUser)",
          "co.com.sapenlinea.liv.core.application.StartSession.start("
              + "co.com.sapenlinea.liv.platform.TokenUser, java.lang.String)",
          "co.com.sapenlinea.liv.core.application.EndSession.end("
              + "java.lang.String, java.lang.String, java.lang.String, java.lang.String)");

  /** Methods that run in another thread or without a request: a permission would always fail. */
  private static final List<String> DETACHED_METHODS =
      List.of(
          "org.springframework.scheduling.annotation.Async",
          "org.springframework.scheduling.annotation.Scheduled",
          "org.springframework.context.event.EventListener",
          "org.springframework.transaction.event.TransactionalEventListener",
          "org.springframework.modulith.events.ApplicationModuleListener");

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

  /**
   * Use cases are created only as {@code @Service}: a bean made by a {@code @Bean} method would
   * have no stereotype, and neither the check nor these rules would see it.
   */
  static ArchRule noBeanMethodsForUseCases(String useCases) {
    return noMethods()
        .that()
        .areMetaAnnotatedWith(Bean.class)
        .should()
        .haveRawReturnType(JavaClass.Predicates.resideInAPackage(useCases))
        .because("the permission check only sees the @Service classes of application (001-16)");
  }

  /**
   * A controller reaches application only through use cases and the types nested in them: a port of
   * application, implemented in infrastructure, would read data without a permission.
   */
  static ArchRule controllersUseOnlyUseCases(String useCases) {
    return classes()
        .that()
        .areMetaAnnotatedWith(Controller.class)
        .should(dependOnApplicationOnlyThroughServices(useCases))
        .because("the permission is checked on the use case, not on the port (001-16)")
        .allowEmptyShould(true);
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
        .should(haveExactlyOneMark())
        .andShould(beInterceptable())
        .because("a hidden option is still reachable by URL; the use case checks (001-16)");
  }

  /**
   * The other methods of a use case are private: a package-private or protected one could be called
   * from another class of the package without any check.
   */
  static ArchRule useCaseMethodsArePublicOrPrivate(String useCases) {
    return methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(useCases)
        .and()
        .areDeclaredInClassesThat()
        .areMetaAnnotatedWith(Component.class)
        .should(bePublicOrPrivate())
        .because("only the public methods of a use case are checked (001-16)");
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
        .andShould(notRunDetachedWithAPermission())
        .because("authorization has one place: the services of application (plan, step 7)")
        .allowEmptyShould(true);
  }

  /**
   * A call or a method reference through {@code this} skips the proxy, and with it the permission
   * of the callee. Nested and anonymous classes of a use case are not looked into: a use case has
   * none today, and a reviewer would see one.
   */
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

  private static ArchCondition<JavaClass> dependOnApplicationOnlyThroughServices(String useCases) {
    DescribedPredicate<JavaClass> inUseCases = JavaClass.Predicates.resideInAPackage(useCases);
    return new ArchCondition<>("depend on application only through @Service use cases") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        for (Dependency dependency : controller.getDirectDependenciesFromSelf()) {
          JavaClass target = dependency.getTargetClass();
          if (inUseCases.test(target)
              && !enclosing(controller).contains(target)
              && enclosing(target).stream().noneMatch(c -> c.isAnnotatedWith(Service.class))) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  /**
   * A type and the classes it is nested in: GetCurrentUser.Me and GetCurrentUser, so a type
   * returned by a use case counts as the use case.
   */
  private static List<JavaClass> enclosing(JavaClass type) {
    List<JavaClass> chain = new ArrayList<>();
    for (Optional<JavaClass> current = Optional.of(type);
        current.isPresent();
        current = current.get().getEnclosingClass()) {
      chain.add(current.get());
    }
    return chain;
  }

  private static ArchCondition<JavaMethod> bePublicOrPrivate() {
    return new ArchCondition<>("be public or private") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaModifier> modifiers = method.getModifiers();
        boolean publicOrPrivate =
            modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PRIVATE);
        events.add(
            new SimpleConditionEvent(
                method,
                publicOrPrivate,
                method.getFullName() + " is package-private or protected: nobody checks it"));
      }
    };
  }

  private static ArchCondition<JavaMethod> notRunDetachedWithAPermission() {
    return new ArchCondition<>("not require a permission when it runs without a request") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean detached = DETACHED_METHODS.stream().anyMatch(method::isMetaAnnotatedWith);
        boolean violated = detached && method.isAnnotatedWith(RequiresPermission.class);
        events.add(
            new SimpleConditionEvent(
                method,
                !violated,
                method.getFullName()
                    + " runs without a request and would always be refused: use @OpenUseCase"));
      }
    };
  }

  private static ArchCondition<JavaMethod> haveExactlyOneMark() {
    return new ArchCondition<>("have exactly one of @RequiresPermission or @OpenUseCase") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        int marks =
            (method.isAnnotatedWith(RequiresPermission.class) ? 1 : 0)
                + (method.isAnnotatedWith(OpenUseCase.class) ? 1 : 0);
        events.add(
            new SimpleConditionEvent(
                method,
                marks == 1,
                method.getFullName()
                    + " needs exactly one of @RequiresPermission or @OpenUseCase"));
      }
    };
  }

  private static ArchCondition<JavaMethod> beInterceptable() {
    return new ArchCondition<>("be neither static nor final") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaModifier> modifiers = method.getModifiers();
        boolean interceptable =
            !modifiers.contains(JavaModifier.STATIC) && !modifiers.contains(JavaModifier.FINAL);
        events.add(
            new SimpleConditionEvent(
                method,
                interceptable,
                method.getFullName() + " is static or final: the proxy cannot check it"));
      }
    };
  }

  private static ArchCondition<JavaClass> notCallTheirOwnGuardedMethods() {
    return new ArchCondition<>("not call or reference their own @RequiresPermission methods") {
      @Override
      public void check(JavaClass useCase, ConditionEvents events) {
        for (JavaMethodCall call : useCase.getMethodCallsFromSelf()) {
          if (isOwnGuarded(useCase, call.getTargetOwner(), call.getTarget().resolveMember())) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
        for (JavaMethodReference reference : useCase.getMethodReferencesFromSelf()) {
          if (isOwnGuarded(
              useCase, reference.getTargetOwner(), reference.getTarget().resolveMember())) {
            events.add(SimpleConditionEvent.violated(reference, reference.getDescription()));
          }
        }
      }
    };
  }

  private static boolean isOwnGuarded(
      JavaClass useCase, JavaClass owner, Optional<JavaMethod> target) {
    return owner.equals(useCase)
        && target.map(method -> method.isAnnotatedWith(RequiresPermission.class)).orElse(false);
  }
}
