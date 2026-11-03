package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import co.com.sapenlinea.liv.core.application.EndSession;
import co.com.sapenlinea.liv.core.application.GetCurrentUser;
import co.com.sapenlinea.liv.core.application.StartSession;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;

/**
 * Authorization lives in one place (plan, step 7): {@code @RequiresPermission} on the public
 * methods of the services of {@code application}, and nowhere else. The rules take the packages of
 * the use cases so that AuthorizationRulesTest can prove them with fixtures.
 */
final class AuthorizationRules {

  /** Where the use cases of the modules live. */
  static final String USE_CASES = "..liv.*.application..";

  /**
   * Use cases without a permission, by design (plan, step 7): the user's own data and menu are open
   * to any user with a session, and the internal session cases are protected by the scope of the
   * service token. GetCapabilities is in platform, outside {@link #USE_CASES}.
   */
  private static final Class<?>[] OPEN_USE_CASES = {
    GetCurrentUser.class, StartSession.class, EndSession.class
  };

  private AuthorizationRules() {}

  /** Every public method of a service of {@code useCases} declares its permission (001-16). */
  static ArchRule useCasesRequirePermission(String useCases) {
    return methods()
        .that()
        .arePublic()
        .and()
        .areNotStatic()
        .and()
        .areDeclaredInClassesThat()
        .resideInAPackage(useCases)
        .and()
        .areDeclaredInClassesThat()
        .areMetaAnnotatedWith(Service.class)
        .and()
        .areDeclaredInClassesThat(not(belongToAnyOf(OPEN_USE_CASES)))
        .should()
        .beAnnotatedWith(RequiresPermission.class)
        .because("a hidden option is still reachable by URL; the use case checks (001-16)")
        // Until the first business use case, the only services of application are open ones.
        .allowEmptyShould(true);
  }

  /** The permission is checked on the use cases only, not on controllers or adapters. */
  static ArchRule permissionsOnlyOnUseCases(String useCases) {
    return methods()
        .that()
        .areAnnotatedWith(RequiresPermission.class)
        .should()
        .bePublic()
        .andShould()
        .beDeclaredInClassesThat()
        .resideInAPackage(useCases)
        .andShould()
        .beDeclaredInClassesThat()
        .areMetaAnnotatedWith(Service.class)
        .because("authorization has one place: the services of application (plan, step 7)")
        .allowEmptyShould(true);
  }
}
