package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.login.LoginAuthorizationRequestResolver;
import co.com.sapenlinea.liv.bff.login.LoginFailureHandler;
import co.com.sapenlinea.liv.bff.login.LoginSuccessHandler;
import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.SessionRepository;

/**
 * The security of the BFF at this stage (feature 001, T17): the sign in with OpenID Connect (code
 * with PKCE, {@code prompt=login}), the session in the server and the sign out. Everything else
 * needs a session and answers 401 without one: the browser, not the BFF, decides where to go.
 *
 * <p>Not here yet: CSRF, the inactivity and absolute limits and the forwarding to {@code /api}
 * (T18), and the hardening of T19. Meanwhile the only cookie, {@code LIV_SESSION}, is {@code
 * SameSite=Strict}, so a request from another site does not carry it.
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
      SecurityContextRepository contexts,
      SessionRepository<?> sessions) {
    http.csrf(csrf -> csrf.disable())
        .securityContext(context -> context.securityContextRepository(contexts))
        .requestCache(RequestCacheConfigurer::disable)
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers("/bff/login", "/login/oauth2/code/*", "/bff/logout")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2Login(login ->
            login
                .loginPage(LoginAuthorizationRequestResolver.LOGIN_PATH)
                .authorizationEndpoint(endpoint ->
                    endpoint.authorizationRequestResolver(
                        new LoginAuthorizationRequestResolver(registrations)))
                .authorizedClientRepository(clients)
                .successHandler(new LoginSuccessHandler(clients, api, contexts, revoker, sessions))
                .failureHandler(new LoginFailureHandler()));
    return http.build();
  }
}
