package co.com.sapenlinea.liv.bff.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A browser for the tests: it never follows a redirect by itself (each hop is visible), keeps the
 * cookies per host and port as a browser does, and remembers every reply of the BFF so a test can
 * look for a token in all of them (001-11).
 */
public final class Browser {

  /** A reply: status, headers (lower case names) and body. */
  public record Reply(int status, Map<String, List<String>> headers, String body) {

    public String location() {
      return header("location");
    }

    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** Every value of a header, not only the first. */
    public List<String> headerValues(String name) {
      return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    public List<String> setCookies() {
      return headers.getOrDefault("set-cookie", List.of());
    }
  }

  /** The steps of a sign in, from the BFF login to the return to LIV. */
  public record SignIn(String authorizeUrl, Reply start, Reply callback) {}

  private final HttpClient http = HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();
  private final Map<String, Map<String, String>> cookies = new LinkedHashMap<>();
  private final List<Reply> bffReplies = new ArrayList<>();
  private final String bffBase;

  public Browser(String bffBase) {
    this.bffBase = bffBase;
  }

  public Reply get(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET(), url);
  }

  /** A GET with the {@code Cookie} header written by hand: two cookies with the same name, say. */
  public Reply getWithCookieHeader(String url, String cookieHeader) {
    return send(
        HttpRequest.newBuilder(URI.create(url)).GET().header("Cookie", cookieHeader), url, false);
  }

  /** A GET with headers, such as the {@code Sec-Fetch-*} a browser adds to a request. */
  public Reply get(String url, Map<String, String> headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
    headers.forEach(request::header);
    return send(request, url);
  }

  /** A POST as the frontend sends it: with the anti-CSRF token of the cookie in the header. */
  public Reply post(String url, String form) {
    return post(url, form, Map.of());
  }

  public Reply post(String url, String form, Map<String, String> headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form));
    String csrf = cookies.getOrDefault(authority(url), Map.of()).get("XSRF-TOKEN");
    if (csrf != null) {
      request.header("X-XSRF-TOKEN", csrf);
    }
    headers.forEach(request::setHeader);
    return send(request, url);
  }

  /** A POST that does not carry the anti-CSRF token, as a form of another site would. */
  public Reply postWithoutCsrfToken(String url, String form) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form)),
        url);
  }

  /**
   * Signs in as a person does: opens the BFF login, fills the form of the provider and comes back
   * to the callback of the BFF.
   */
  public SignIn signIn(String returnTo) {
    String loginUrl =
        bffBase
            + "/bff/login"
            + (returnTo == null
                ? ""
                : "?returnTo="
                    + java.net.URLEncoder.encode(
                        returnTo, java.nio.charset.StandardCharsets.UTF_8));
    Reply start = get(loginUrl);
    String authorizeUrl = start.location();
    Reply form = post(authorizeUrl, "username=" + OidcProvider.SUBJECT);
    Reply callback = get(form.location());
    return new SignIn(authorizeUrl, start, callback);
  }

  /** Every reply of the BFF this browser received, with its headers and body. */
  public List<Reply> bffReplies() {
    return List.copyOf(bffReplies);
  }

  public String cookie(String name) {
    return cookies.getOrDefault(authority(bffBase), Map.of()).get(name);
  }

  public void setCookie(String name, String value) {
    cookies.computeIfAbsent(authority(bffBase), key -> new LinkedHashMap<>()).put(name, value);
  }

  private Reply send(HttpRequest.Builder request, String url) {
    return send(request, url, true);
  }

  private Reply send(HttpRequest.Builder request, String url, boolean withJar) {
    String authority = authority(url);
    Map<String, String> jar = cookies.getOrDefault(authority, Map.of());
    if (withJar && !jar.isEmpty()) {
      request.header(
          "Cookie",
          String.join(
              "; ", jar.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
    }
    try {
      HttpResponse<String> response = http.send(request.build(), BodyHandlers.ofString());
      Reply reply = new Reply(response.statusCode(), lowerCased(response), response.body());
      reply.setCookies().forEach(setCookie -> store(authority, setCookie));
      if (authority.equals(authority(bffBase))) {
        bffReplies.add(reply);
      }
      return reply;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void store(String authority, String setCookie) {
    String pair = setCookie.split(";", 2)[0];
    int equals = pair.indexOf('=');
    String name = pair.substring(0, equals).trim();
    String value = pair.substring(equals + 1).trim();
    boolean expired = value.isEmpty() || setCookie.toLowerCase(Locale.ROOT).contains("max-age=0");
    Map<String, String> jar = cookies.computeIfAbsent(authority, key -> new LinkedHashMap<>());
    if (expired) {
      jar.remove(name);
    } else {
      jar.put(name, value);
    }
  }

  private static Map<String, List<String>> lowerCased(HttpResponse<?> response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    response
        .headers()
        .map()
        .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values));
    return headers;
  }

  private static String authority(String url) {
    URI uri = URI.create(url);
    return uri.getHost() + ":" + uri.getPort();
  }
}
