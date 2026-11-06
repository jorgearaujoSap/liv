package co.com.sapenlinea.liv.bff.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The hardening of the BFF (doc. 02 §8; T19): the security headers on every answer and the limit of
 * requests. The limit is applied in the security filters ({@link SecurityConfiguration}), where the
 * person is known.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class HardeningConfiguration {

  /** First of all the filters, so that every answer has them, also an error. */
  @Bean
  FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
    FilterRegistrationBean<SecurityHeadersFilter> registration =
        new FilterRegistrationBean<>(new SecurityHeadersFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  RateLimiter rateLimiter() {
    return new RateLimiter();
  }

  @Bean
  RateLimitFilter rateLimitFilter(RateLimiter limiter, RateLimitProperties limits) {
    return new RateLimitFilter(limiter, limits);
  }

  /** It is a filter of the security chain only, not one of the whole server. */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterOutsideTheChain(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  RateLimitCleaner rateLimitCleaner(RateLimiter limiter, RateLimitFilter filter) {
    return new RateLimitCleaner(limiter, filter);
  }

  /** Every minute, the buckets full again are forgotten, in each replica. */
  static class RateLimitCleaner {

    private final RateLimiter limiter;
    private final RateLimitFilter filter;

    RateLimitCleaner(RateLimiter limiter, RateLimitFilter filter) {
      this.limiter = limiter;
      this.filter = filter;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void forgetTheFull() {
      limiter.forgetTheFull(filter.limitsByPrefix());
    }
  }
}
