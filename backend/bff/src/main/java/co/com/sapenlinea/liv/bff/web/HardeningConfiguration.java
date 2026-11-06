package co.com.sapenlinea.liv.bff.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * The hardening of the BFF (doc. 02 §8; T19): the security headers on every answer and the limit of
 * requests. The limit is applied in the security filters ({@link SecurityConfiguration}), where the
 * person is known.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class HardeningConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(HardeningConfiguration.class);

  /** The default of LIV_BFF_TRUSTED_PROXIES in application.yml: this machine only. */
  static final String DEFAULT_TRUSTED_PROXIES = "127[.]0[.]0[.]1|0:0:0:0:0:0:0:1";

  /** First of all the filters, so that every answer has them, also an error. */
  @Bean
  FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
    FilterRegistrationBean<SecurityHeadersFilter> registration =
        new FilterRegistrationBean<>(new SecurityHeadersFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  RateLimiter rateLimiter(
      Environment environment,
      @Value("${server.tomcat.remoteip.internal-proxies:}") String trustedProxies) {
    warnIfTheProxiesAreTheLocalOnes(environment, trustedProxies);
    return new RateLimiter();
  }

  /**
   * Behind the load balancer, without {@code LIV_BFF_TRUSTED_PROXIES} every person seems to come
   * from the balancer (one limit for all) and the scheme is lost. It is said at start, out of the
   * local profiles.
   */
  static void warnIfTheProxiesAreTheLocalOnes(Environment environment, String trustedProxies) {
    boolean local = environment.matchesProfiles("dev | test");
    if (!local && DEFAULT_TRUSTED_PROXIES.equals(trustedProxies)) {
      LOG.warn(
          "LIV_BFF_TRUSTED_PROXIES no está definida: el BFF solo confía en esta máquina. Detrás de"
              + " un balanceador, todas las personas parecerán venir de él");
    }
  }

  @Bean
  RateLimitFilter rateLimitFilter(RateLimiter limiter, RateLimitProperties limits) {
    return new RateLimitFilter(limiter, limits);
  }

  /**
   * The limit runs in the security chain, where the person is known; and also, by address, in front
   * of {@code /bff/session}, which is answered before that chain ({@link SessionStatusFilter}). A
   * request is limited once: the filter runs once per request.
   */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitInFrontOfTheSessionStatus(
      RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns(SessionStatusFilter.PATH);
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 20);
    return registration;
  }

  @Bean
  RateLimitCleaner rateLimitCleaner(RateLimiter limiter) {
    return new RateLimitCleaner(limiter);
  }

  /** Every minute, the buckets full again are forgotten, in each replica. */
  static class RateLimitCleaner {

    private final RateLimiter limiter;

    RateLimitCleaner(RateLimiter limiter) {
      this.limiter = limiter;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void forgetTheFull() {
      limiter.forgetTheFull();
    }
  }
}
