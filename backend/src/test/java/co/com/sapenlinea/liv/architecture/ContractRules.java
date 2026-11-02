package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.function.RouterFunction;

/**
 * Contract-first API (ADR 0005, docs/arquitectura/01 §10): every controller implements an interface
 * generated from backend/contracts/openapi, and its routes come only from that interface. The
 * generated interfaces live in {@code <module>.api.contract} (and {@code .internal});
 * ContractRulesTest checks that nobody writes code by hand in those packages.
 */
final class ContractRules {

  /** Packages where openapi-generator writes the interfaces (one per contract, see pom.xml). */
  static final Pattern GENERATED_CONTRACT_PACKAGE =
      Pattern.compile("co\\.com\\.sapenlinea\\.liv\\.[a-z]+\\.api\\.contract(\\.internal)?");

  static final ArchRule CONTROLLERS_IMPLEMENT_GENERATED_CONTRACTS =
      classes()
          .that()
          .areMetaAnnotatedWith(Controller.class)
          .should(implementAGeneratedContract())
          .andShould(declareNoRoutesOfTheirOwn())
          .because("the OpenAPI contract comes first and the code is generated from it (ADR 0005)")
          // There are no controllers until T12-T14; the fixtures in ContractRulesTest exercise it.
          .allowEmptyShould(true);

  /** Functional routes and raw handlers would publish endpoints without a contract. */
  static final ArchRule NO_ROUTES_WITHOUT_CONTRACT =
      noClasses()
          .should()
          .dependOnClassesThat()
          .areAssignableTo(RouterFunction.class)
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(HttpRequestHandler.class)
          .because("every route is declared in the OpenAPI contract (ADR 0005)")
          .allowEmptyShould(true);

  private ContractRules() {}

  private static ArchCondition<JavaClass> implementAGeneratedContract() {
    return new ArchCondition<>("implement an interface generated from the OpenAPI contract") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        boolean implementsContract =
            controller.getAllRawInterfaces().stream()
                .anyMatch(i -> GENERATED_CONTRACT_PACKAGE.matcher(i.getPackageName()).matches());
        events.add(
            new SimpleConditionEvent(
                controller,
                implementsContract,
                controller.getName() + " does not implement a generated contract interface"));
      }
    };
  }

  /**
   * Routes declared outside the generated interfaces would bypass the contract (ADR 0005). Spring
   * finds mappings in the whole type hierarchy, so the controller, its superclasses and any
   * hand-written interface are checked.
   */
  private static ArchCondition<JavaClass> declareNoRoutesOfTheirOwn() {
    return new ArchCondition<>("declare no request mappings outside the generated contract") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        List<JavaClass> hierarchy = new ArrayList<>();
        hierarchy.add(controller);
        hierarchy.addAll(controller.getAllRawSuperclasses());
        hierarchy.addAll(controller.getAllRawInterfaces());
        for (JavaClass type : hierarchy) {
          if (GENERATED_CONTRACT_PACKAGE.matcher(type.getPackageName()).matches()) {
            continue;
          }
          if (type.isMetaAnnotatedWith(RequestMapping.class)) {
            events.add(
                SimpleConditionEvent.violated(
                    controller,
                    controller.getName() + " gets a class-level mapping from " + type.getName()));
          }
          for (JavaMethod method : type.getMethods()) {
            if (method.isMetaAnnotatedWith(RequestMapping.class)) {
              events.add(
                  SimpleConditionEvent.violated(
                      controller,
                      controller.getName() + " gets the route " + method.getFullName()));
            }
          }
        }
      }
    };
  }
}
