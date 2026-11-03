package co.com.sapenlinea.liv.platform.security;

import co.com.sapenlinea.liv.platform.UserDirectory;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless resource server (doc. 02 §3, plan step 7), one chain per kind of caller:
 *
 * <ol>
 *   <li>{@code /internal/**}: only the BFF, with its service token; session start also needs the
 *       user token;
 *   <li>{@code /api/**}: user tokens, then the user is resolved in the LIV registry;
 *   <li>everything else: only health and info, the rest is denied.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LivSecurityProperties.class)
class SecurityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

  @Bean
  @Primary
  JwtDecoder userTokenDecoder(LivSecurityProperties properties) {
    if (properties.allowHttpIssuer()) {
      LOG.warn("liv.security.allow-http-issuer is on: only for dev and tests (doc. 02 §3)");
    }
    return TokenDecoders.userTokens(properties);
  }

  @Bean
  JwtDecoder serviceTokenDecoder(LivSecurityProperties properties) {
    return TokenDecoders.serviceTokens(properties);
  }

  @Bean
  ProblemResponses problemResponses(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return new ProblemResponses(resolver);
  }

  @Bean
  @Order(1)
  SecurityFilterChain internalChain(
      HttpSecurity http,
      @Qualifier("serviceTokenDecoder") JwtDecoder serviceTokens,
      @Qualifier("userTokenDecoder") JwtDecoder userTokens,
      LivSecurityProperties properties,
      ProblemResponses problems) {
    stateless(http.securityMatcher("/internal/**"), problems);
    http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .oauth2ResourceServer(server ->
            server
                .jwt(jwt -> jwt.decoder(serviceTokens))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
        .addFilterAfter(
            new UserTokenFilter(
                PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/internal/v1/sessions"),
                userTokens,
                new ClaimsAdapter(properties.claims())),
            AuthorizationFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiChain(
      HttpSecurity http,
      @Qualifier("userTokenDecoder") JwtDecoder userTokens,
      UserDirectory directory,
      LivSecurityProperties properties,
      ProblemResponses problems) {
    stateless(http.securityMatcher("/api/**"), problems);
    http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .oauth2ResourceServer(server ->
            server
                .jwt(jwt -> jwt.decoder(userTokens))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
        .addFilterAfter(
            new CurrentUserFilter(directory, new ClaimsAdapter(properties.claims())),
            AuthorizationFilter.class);
    return http.build();
  }

  @Bean
  @Order(3)
  SecurityFilterChain defaultChain(HttpSecurity http, ProblemResponses problems) {
    stateless(http, problems);
    http.authorizeHttpRequests(requests ->
        requests
            .dispatcherTypeMatchers(DispatcherType.ERROR)
            .permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
            .permitAll()
            .anyRequest()
            .denyAll());
    return http.build();
  }

  /** Bearer tokens only: no session, no CSRF (no cookies), no saved requests, no login pages. */
  private static void stateless(HttpSecurity http, ProblemResponses problems) {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .requestCache(cache -> cache.disable())
        .exceptionHandling(
            handling -> handling.authenticationEntryPoint(problems).accessDeniedHandler(problems));
  }
}
