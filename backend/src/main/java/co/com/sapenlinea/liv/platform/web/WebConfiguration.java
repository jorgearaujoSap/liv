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

  /** Right after the correlation id, before security: it answers what the other filters throw. */
  @Bean
  FilterRegistrationBean<ProblemFallbackFilter> problemFallbackFilter(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    FilterRegistrationBean<ProblemFallbackFilter> registration =
        new FilterRegistrationBean<>(new ProblemFallbackFilter(resolver));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
