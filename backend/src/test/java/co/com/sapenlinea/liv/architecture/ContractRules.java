package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

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

  /** Routes declared on the controller itself would bypass the contract (ADR 0005). */
  private static ArchCondition<JavaClass> declareNoRoutesOfTheirOwn() {
    return new ArchCondition<>("declare no request mappings of their own") {
      @Override
      public void check(JavaClass controller, ConditionEvents events) {
        if (controller.isMetaAnnotatedWith(RequestMapping.class)) {
          events.add(
              SimpleConditionEvent.violated(
                  controller, controller.getName() + " declares a class-level request mapping"));
        }
        for (JavaMethod method : controller.getMethods()) {
          if (method.isMetaAnnotatedWith(RequestMapping.class)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " declares a route outside the contract"));
          }
        }
      }
    };
  }
}
