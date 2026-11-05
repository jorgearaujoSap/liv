package co.com.sapenlinea.liv.bff.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The other side of the BFF in the tests: the internal endpoints of liv-api ({@code /internal/v1},
 * contract core-internal.yaml) and the revocation endpoint of the identity provider (RFC 7009). It
 * records every call, in order, and answers with the status each test sets.
 */
public final class FakeBackend {

  /** A call received: its method, path, headers (lower case names) and body. */
  public record Call(String method, String path, Map<String, String> headers, String body) {

    public String header(String name) {
      return headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }
  }

  private static final String START_SESSION = "/internal/v1/sessions";
  private static final String REVOKE = "/revoke";

  private final HttpServer server;
  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private final AtomicInteger startSessionStatus = new AtomicInteger(201);
  private final AtomicInteger endSessionStatus = new AtomicInteger(204);
  private final AtomicInteger revokeStatus = new AtomicInteger(200);
  private volatile long startSessionDelayMillis;
  private volatile long apiDelayMillis;
  private final Map<String, String> apiHeaders = new java.util.concurrent.ConcurrentHashMap<>();
  private final AtomicInteger apiStatus = new AtomicInteger(200);
  private volatile String apiBody = "{\"ok\":true}";

  public FakeBackend() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.createContext("/", this::handle);
    server.start();
  }

  public String apiBaseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public String revocationUri() {
    return apiBaseUrl() + REVOKE;
  }

  /** Forgets the calls and answers the usual statuses again. */
  public void reset() {
    calls.clear();
    startSessionStatus.set(201);
    endSessionStatus.set(204);
    revokeStatus.set(200);
    startSessionDelayMillis = 0;
    apiDelayMillis = 0;
    apiHeaders.clear();
    apiStatus.set(200);
    apiBody = "{\"ok\":true}";
  }

  /** A header the public API adds to its answers, such as a cache directive or a cookie. */
  public void apiAnswersWithHeader(String name, String value) {
    apiHeaders.put(name, value);
  }

  /** The public API answers only after this long: a slow API. */
  public void apiAnswersAfter(long millis) {
    apiDelayMillis = millis;
  }

  /** What the public API (/api/**) answers to what the BFF forwards. */
  public void apiAnswers(int status, String body) {
    apiStatus.set(status);
    apiBody = body;
  }

  /** The requests the BFF forwarded to the public API. */
  public List<Call> apiCalls() {
    return calls.stream().filter(call -> call.path().startsWith("/api/")).toList();
  }

  /** StartSession answers only after this long: a slow API. */
  public void startSessionAnswersAfter(long millis) {
    startSessionDelayMillis = millis;
  }

  public void startSessionAnswers(int status) {
    startSessionStatus.set(status);
  }

  public void endSessionAnswers(int status) {
    endSessionStatus.set(status);
  }

  public void revocationAnswers(int status) {
    revokeStatus.set(status);
  }

  public List<Call> calls() {
    return List.copyOf(calls);
  }

  public List<Call> callsTo(String path) {
    return calls.stream().filter(call -> call.path().equals(path)).toList();
  }

  public List<Call> startSessionCalls() {
    return callsTo(START_SESSION);
  }

  public List<Call> endSessionCalls() {
    return calls.stream().filter(call -> call.path().endsWith("/end")).toList();
  }

  public List<Call> revocationCalls() {
    return callsTo(REVOKE);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, String> headers = new java.util.HashMap<>();
    exchange
        .getRequestHeaders()
        .forEach(
            (name, values) -> headers.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0)));
    String path = exchange.getRequestURI().getPath();
    calls.add(new Call(exchange.getRequestMethod(), path, headers, body));

    int status;
    String answer = "";
    if (path.equals(START_SESSION)) {
      sleep(startSessionDelayMillis);
      status = startSessionStatus.get();
      answer = "{\"userId\":\"8f0f6b1e-5a52-4d9b-a2a2-0d6c5d3b7a10\"}";
    } else if (path.endsWith("/end")) {
      status = endSessionStatus.get();
    } else if (path.equals(REVOKE)) {
      status = revokeStatus.get();
    } else if (path.startsWith("/api/")) {
      sleep(apiDelayMillis);
      apiHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
      status = apiStatus.get();
      answer = apiBody;
    } else {
      status = 404;
    }
    byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }
}
