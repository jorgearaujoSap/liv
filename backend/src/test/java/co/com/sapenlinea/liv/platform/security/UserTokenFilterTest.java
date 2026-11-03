package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@DisplayName("Filtro de X-User-Token (T12)")
class UserTokenFilterTest {

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un token mal formado o con firma inválida es un rechazo (401)")
  void badTokenIsRejected() {
    UserTokenFilter filter =
        filter(token -> {
          throw new BadJwtException("firma inválida");
        });

    assertThatThrownBy(() -> run(filter)).isInstanceOf(AuthenticationRejectedException.class);
  }

  @Test
  @DisplayName("Doc. 02 §8 · Si no se pueden leer las claves del proveedor no es un rechazo")
  void providerFailureIsNotARejection() {
    UserTokenFilter filter =
        filter(token -> {
          throw new JwtException("no se pudo descargar el JWKS");
        });

    assertThatThrownBy(() -> run(filter))
        .isInstanceOf(JwtException.class)
        .isNotInstanceOf(AuthenticationRejectedException.class);
  }

  private static UserTokenFilter filter(JwtDecoder decoder) {
    return new UserTokenFilter(
        request -> true,
        decoder,
        new ClaimsAdapter(new LivSecurityProperties.Claims("administration_id", "roles")));
  }

  private static void run(UserTokenFilter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/sessions");
    request.addHeader(UserTokenFilter.HEADER, "eyJ.eyJ.firma");
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
  }
}
