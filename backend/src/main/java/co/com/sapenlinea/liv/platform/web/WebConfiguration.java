package co.com.sapenlinea.liv.platform.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
class WebConfiguration {

  /** First of all filters, so rejections by the security chain carry the correlation id too. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * After the correlation id and inside Spring Boot's observation filter ({@code HIGHEST_PRECEDENCE
   * + 1}), so metrics see the final status; before security, so it answers what the other filters
   * throw.
   */
  @Bean
  FilterRegistrationBean<ProblemFallbackFilter> problemFallbackFilter(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    FilterRegistrationBean<ProblemFallbackFilter> registration =
        new FilterRegistrationBean<>(new ProblemFallbackFilter(resolver));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    return registration;
  }

  /**
   * Inside the fallback, so a leaked context is answered as a 500 Problem Details, and before
   * security, so no authenticated filter runs with an inherited context.
   */
  @Bean
  FilterRegistrationBean<LeakedContextFilter> leakedContextFilter() {
    FilterRegistrationBean<LeakedContextFilter> registration =
        new FilterRegistrationBean<>(new LeakedContextFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
    return registration;
  }
}
