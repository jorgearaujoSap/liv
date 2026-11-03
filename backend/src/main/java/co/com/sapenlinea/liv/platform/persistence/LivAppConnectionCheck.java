package co.com.sapenlinea.liv.platform.persistence;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Every new connection of the application checks that it can never read past RLS (doc. 02 §5.2, T10
 * › T12): the session user and the role are {@code liv_app}, which is not a superuser, has no
 * {@code BYPASSRLS} and no memberships (being a member of the owner would make it the owner of the
 * tables), and the role or the database do not preset an RLS context. Otherwise the connection
 * fails with the reason, so a deployment that changes the user gets no connection at all.
 *
 * <p>It is imposed after the properties are bound, so no {@code spring.datasource.hikari.*}
 * property replaces it; a pool other than Hikari is refused.
 */
class LivAppConnectionCheck implements BeanPostProcessor {

  /** Casting a text to boolean fails on purpose: PostgreSQL returns the text in the error. */
  static final String SQL =
      """
      SELECT CASE
        WHEN session_user = 'liv_app' AND current_user = 'liv_app'
          AND NOT r.rolsuper AND NOT r.rolbypassrls
          AND NOT EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid)
          AND coalesce(current_setting('app.administration_id', true), '') = ''
          AND coalesce(current_setting('app.platform_job', true), '') = ''
        THEN true
        ELSE CAST('LIV solo se conecta como liv_app sin superusuario, BYPASSRLS, membresías ni'
          || ' contexto, no como ' || session_user AS boolean)
      END
      FROM pg_roles r WHERE r.rolname = current_user
      """;

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof HikariDataSource pool) {
      pool.setConnectionInitSql(SQL);
    } else if (bean instanceof DataSource) {
      throw new IllegalStateException(
          "The connection check of liv_app needs a Hikari pool, not " + bean.getClass());
    }
    return bean;
  }
}
