package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.login.LoginAuthorizationRequestResolver;
import co.com.sapenlinea.liv.bff.login.LoginFailureHandler;
import co.com.sapenlinea.liv.bff.login.LoginSuccessHandler;
import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.SessionLifetime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.session.SessionRepository;

/**
 * The security of the BFF (feature 001, T17 and T18): the sign in with OpenID Connect (code with
 * PKCE, {@code prompt=login}), the session in the server, the sign out, the anti-CSRF token and the
 * maximum duration of the session. Everything else needs a session and answers 401 without one: the
 * browser, not the BFF, decides where to go. {@code /api/**} goes to the API ({@link
 * ApiRouteConfiguration}); {@code /internal/**} has no route, so it is a 404.
 *
 * <p>The hardening of T19 (rate limit, headers beyond the default {@code Cache-Control}) is not
 * here yet.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  /** The context lives in the session, which Spring Session keeps in the database. */
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository clients,
      InternalApiClient api,
      RefreshTokenRevoker revoker,
      SessionCloser closer,
      SecurityContextRepository contexts,
      SessionRepository<?> sessions,
      SessionLifetime lifetime) {
    // The frontend reads the cookie (no HttpOnly) and sends it back in the X-XSRF-TOKEN header
    // (001-12). The cookie is no credential: LIV_SESSION is the one that authenticates.
    CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
    http.csrf(csrf ->
            csrf.csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler()))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .addFilterAfter(
            new AbsoluteLimitFilter(lifetime, closer), SecurityContextHolderFilter.class)
        .securityContext(context -> context.securityContextRepository(contexts))
        .requestCache(RequestCacheConfigurer::disable)
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(
                    LoginAuthorizationRequestResolver.LOGIN_PATH,
                    "/login/oauth2/code/*",
                    "/bff/logout",
                    // No route: a 404 for everybody, not a 401 that says it exists. The 404 is
                    // served by /error, which a person without a session must reach.
                    "/internal/**",
                    "/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(CsrfRejection.handler()))
        .oauth2Login(login ->
            login
                .loginPage(LoginAuthorizationRequestResolver.LOGIN_PATH)
                .authorizationEndpoint(endpoint ->
                    endpoint.authorizationRequestResolver(
                        new LoginAuthorizationRequestResolver(registrations, closer)))
                .authorizedClientRepository(clients)
                .successHandler(
                    new CsrfTokenAfterLogin(
                        new LoginSuccessHandler(
                            clients, api, contexts, revoker, sessions, lifetime),
                        csrfTokens))
                .failureHandler(new LoginFailureHandler(contexts)));
    return http.build();
  }
}
