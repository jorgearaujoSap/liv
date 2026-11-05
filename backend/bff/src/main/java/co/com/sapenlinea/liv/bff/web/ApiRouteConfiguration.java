package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.TokenRenewal;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The only route of the gateway: {@code /api/**} goes to liv-api (ADR 0010, plan, step 10) with the
 * access token of the session. What the browser sends as a credential is dropped on the way: its
 * own {@code Authorization}, the cookie of the session, {@code X-User-Token} and the anti-CSRF
 * header never reach the API. {@code /internal/**} is not routed.
 */
@Configuration(proxyBeanMethods = false)
class ApiRouteConfiguration {

  @Bean
  RouterFunction<ServerResponse> apiRoute(
      BffProperties properties, TokenRenewal renewal, SessionCloser closer) {
    return GatewayRouterFunctions.route("liv-api")
        .route(RequestPredicates.path("/api/**"), HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri(properties.api().baseUrl()))
        .before(BeforeFilterFunctions.removeRequestHeader("Cookie"))
        .before(BeforeFilterFunctions.removeRequestHeader("X-User-Token"))
        .before(BeforeFilterFunctions.removeRequestHeader("X-XSRF-TOKEN"))
        .filter(new TokenRelayFilter(renewal, closer))
        .build();
  }
}
