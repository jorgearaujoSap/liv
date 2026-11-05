package co.com.sapenlinea.liv.bff.session;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Every new connection of the BFF checks, when it is created, that it works as {@code liv_bff}
 * (doc. 02 §5.2, T16): the session user and the role are {@code liv_bff}, which is not a superuser
 * and has none of the privileged attributes, no memberships (a member of {@code liv_bff_owner} acts
 * as its owner) and owns no table or schema. Otherwise the connection fails with the reason, so a
 * deployment that changes the user gets no connection at all. It is the same idea as the check of
 * liv-api for {@code liv_app}.
 *
 * <p>It is imposed after the properties are bound, so no {@code spring.datasource.hikari.*}
 * property replaces it; a pool other than Hikari is refused.
 */
public final class BffConnectionCheck implements BeanPostProcessor {

  /** Casting a text to boolean fails on purpose: PostgreSQL returns the text in the error. */
  static final String SQL =
      """
      SELECT CASE
        WHEN session_user = 'liv_bff' AND current_user = 'liv_bff'
          AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreaterole
          AND NOT r.rolcreatedb AND NOT r.rolreplication
          AND NOT EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid)
          AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.relowner = r.oid)
          AND NOT EXISTS (SELECT 1 FROM pg_namespace n WHERE n.nspowner = r.oid)
        THEN true
        ELSE CAST('El BFF solo se conecta como liv_bff sin privilegios, membresías ni objetos'
          || ' propios, no como ' || session_user AS boolean)
      END
      FROM pg_roles r WHERE r.rolname = current_user
      """;

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof HikariDataSource pool) {
      pool.setConnectionInitSql(SQL);
    } else if (bean instanceof DataSource) {
      throw new IllegalStateException(
          "The connection check of liv_bff needs a Hikari pool, not " + bean.getClass());
    }
    return bean;
  }
}
