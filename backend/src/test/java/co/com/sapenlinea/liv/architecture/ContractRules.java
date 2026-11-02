package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract-first API (ADR 0005, docs/arquitectura/01 §10): every controller implements an interface
 * generated from backend/contracts/openapi. The generated interfaces live in {@code
 * ..api.contract..}; nobody writes them by hand.
 */
final class ContractRules {

  static final ArchRule CONTROLLERS_IMPLEMENT_GENERATED_CONTRACTS =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should(implementAGeneratedContract())
          .because("the OpenAPI contract comes first and the code is generated from it (ADR 0005)")
          .allowEmptyShould(true);

  private ContractRules() {}

  private static ArchCondition<JavaClass> implementAGeneratedContract() {
    return new ArchCondition<>("implement an interface generated from the OpenAPI contract") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        boolean implementsContract =
            controller.getAllRawInterfaces().stream()
                .anyMatch(i -> i.getPackage().getName().contains(".api.contract"));
        events.add(
            new SimpleConditionEvent(
                controller,
                implementsContract,
                controller.getName() + " does not implement a generated contract interface"));
      }
    };
  }
}
