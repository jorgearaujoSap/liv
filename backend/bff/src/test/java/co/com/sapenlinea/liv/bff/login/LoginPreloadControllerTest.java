package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The page asks for {@code /bff/login} again as a navigation. If a navigation ever reached it (the
 * sign in filter and this mapping matching different addresses), the page would ask for itself over
 * and over: it answers 404 without the script instead.
 */
@Tag("001-01")
class LoginPreloadControllerTest {

  @Test
  @DisplayName(
      "001-01 · Si una navegación llegara a la página de la precarga, no recibe el script que la"
          + " vuelve a pedir")
  void aNavigationNeverGetsThePageThatAsksForItself() throws Exception {
    MockHttpServletRequest navigation = new MockHttpServletRequest("GET", "/bff/login");
    navigation.addHeader("Sec-Fetch-Dest", "document");
    navigation.addHeader("Sec-Fetch-Mode", "navigate");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new LoginPreloadController().preload(navigation, response);

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getContentAsString()).doesNotContain("<script");
  }
}
