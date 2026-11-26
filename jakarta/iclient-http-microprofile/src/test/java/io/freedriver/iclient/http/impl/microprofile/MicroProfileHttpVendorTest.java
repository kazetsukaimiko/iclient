package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.api.IClient;
import io.freedriver.iclient.api.IClientVendor;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.http.api.call.HttpBody;
import io.freedriver.iclient.http.api.call.ImmutableHttpRequest;
import io.freedriver.iclient.http.api.config.HttpClientConfig;
import io.freedriver.iclient.http.api.exception.IClientHttpCallException;
import io.freedriver.iclient.http.api.modifiers.IClientHttpRequestFilter;
import io.freedriver.iclient.http.tck.HttpVendorContract;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs {@link HttpVendorContract} on RESTEasy's MicroProfile client. */
class MicroProfileHttpVendorTest extends HttpVendorContract<MicroProfileHttpVendorTest.Probe> {
    private final IClientVendor vendor = new MicroProfileHttpVendor();
    private final AtomicReference<ImmutableHttpRequest> sent = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> hold = new AtomicReference<>();
    private volatile StubResponse stub = new StubResponse(200, Map.of(), new byte[0]);
    private HttpServer server;
    private ExecutorService executor;

    @Path("/")
    public interface Probe {
        @GET
        @Path("res")
        String res();
    }

    public interface Plain {
        String res();
    }

    @BeforeEach
    void startStub() throws IOException {
        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        HttpStatusMapper.entityStreamView = UnaryOperator.identity();
    }

    @Override
    protected IClientVendor vendor() {
        return vendor;
    }

    @Override
    protected Class<Probe> apiClass() {
        return Probe.class;
    }

    @Override
    protected HttpClientConfig config() {
        return HttpClientConfig.builder().baseUri(URI.create(root())).build();
    }

    @Override
    protected void call(Probe client) {
        client.res();
    }

    @Override
    protected void stub(int status, Map<String, List<String>> headers, byte[] body) {
        sent.set(null);
        stub = new StubResponse(status, headers, body);
    }

    @Override
    protected Optional<ImmutableHttpRequest> sentRequest() {
        return Optional.ofNullable(sent.get());
    }

    @Test
    public void basePathWithAndWithoutTrailingSlashReachTheSameResource() throws Exception {
        var expected = URI.create(root() + "ctx/res");
        for (var base : List.of(root() + "ctx", root() + "ctx/")) {
            stub(200, Map.of(), new byte[0]);
            var client = IClient.create(Probe.class, HttpClientConfig.builder().baseUri(URI.create(base)).build());
            call(client);
            assertEquals(expected, sentRequest().orElseThrow().uri());
        }
    }

    @Test
    public void redirectDoesNotReachASecondListener() throws Exception {
        var otherHits = new AtomicInteger();
        var otherExecutor = Executors.newSingleThreadExecutor();
        var other = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        other.setExecutor(otherExecutor);
        other.createContext("/", exchange -> {
            otherHits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        other.start();
        try {
            var location = "http://127.0.0.1:" + other.getAddress().getPort() + "/land";
            stub(302, Map.of("Location", List.of(location)), "away".getBytes(StandardCharsets.US_ASCII));
            var client = IClient.create(Probe.class, config(), (IClientHttpRequestFilter) (request, call) ->
                    request.headers().put("X-Redirect-Secret", List.of("do-not-follow")));
            try {
                call(client);
            } catch (RuntimeException ignored) {
                // A 302 has no successful string entity. The second listener is the assertion.
            }
            assertEquals(0, otherHits.get());
            assertEquals(List.of("do-not-follow"), sentRequest().orElseThrow().headers().get("X-Redirect-Secret"));
        } finally {
            other.stop(0);
            otherExecutor.shutdownNow();
        }
    }

    @Test
    public void filterFailureMessageOmitsTheCauseMessage() throws Exception {
        var error = new IOException("secret-token-do-not-leak");
        var client = IClient.create(Probe.class, config(), (IClientHttpRequestFilter) (request, call) -> {
            throw error;
        });
        stub(200, Map.of(), new byte[0]);
        var failure = assertThrows(IClientCallException.class, () -> call(client));
        assertSame(error, failure.getCause());
        assertFalse(failure.getMessage().contains("secret-token-do-not-leak"));
        assertFalse(sentRequest().isPresent());
    }

    @Test
    public void interfaceWithoutPathIsDeclined() throws Exception {
        assertEquals(Optional.empty(), vendor().createClient(Plain.class, config()));
    }

    @Test
    public void errorEntityReadStopsAfterTheCaptureLimit() throws Exception {
        byte[] body = new byte[IClientHttpCallException.CAPTURE_LIMIT + 64];
        Arrays.fill(body, (byte) 'z');
        var counter = new AtomicInteger();
        HttpStatusMapper.entityStreamView = raw -> new FilterInputStream(raw) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    counter.incrementAndGet();
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                if (count > 0) {
                    counter.addAndGet(count);
                }
                return count;
            }
        };
        stub(500, Map.of(), body);
        var client = IClient.create(Probe.class, config());
        var failure = assertThrows(IClientHttpCallException.class, () -> call(client));
        assertTrue(failure.bodyTruncated());
        assertEquals(IClientHttpCallException.CAPTURE_LIMIT + 1, counter.get());
    }

    @Test
    public void overlappingCallsKeepTheirOwnSentRequests() throws Exception {
        var entered = new CountDownLatch(2);
        var mark = new ThreadLocal<String>();
        IClientHttpRequestFilter filter = (request, call) -> {
            request.headers().put("X-Call", List.of(mark.get()));
            entered.countDown();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        };
        var client = IClient.create(Probe.class, config(), filter);
        stub(500, Map.of(), "no".getBytes(StandardCharsets.US_ASCII));
        hold.set(new CountDownLatch(2));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var alpha = pool.submit(() -> failWith(client, mark, "alpha"));
            var beta = pool.submit(() -> failWith(client, mark, "beta"));
            assertEquals(List.of("alpha"), alpha.get(10, TimeUnit.SECONDS).request().headers().get("X-Call"));
            assertEquals(List.of("beta"), beta.get(10, TimeUnit.SECONDS).request().headers().get("X-Call"));
        } finally {
            hold.set(null);
            pool.shutdownNow();
        }
    }

    private IClientHttpCallException failWith(Probe client, ThreadLocal<String> mark, String value) {
        mark.set(value);
        return assertThrows(IClientHttpCallException.class, () -> call(client));
    }

    private String root() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private void handle(HttpExchange exchange) throws IOException {
        var gate = hold.get();
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String type = requestBody.length == 0 ? null : exchange.getRequestHeaders().getFirst("Content-Type");
        sent.set(new ImmutableHttpRequest(exchange.getRequestMethod(), observedUri(exchange),
                exchange.getRequestHeaders(), HttpBody.ofBytes(requestBody, type)));
        if (gate != null) {
            gate.countDown();
            try {
                if (!gate.await(5, TimeUnit.SECONDS)) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
        }
        var response = stub;
        response.headers().forEach((name, values) ->
                values.forEach(value -> exchange.getResponseHeaders().add(name, value)));
        exchange.sendResponseHeaders(response.status(), response.body().length);
        exchange.getResponseBody().write(response.body());
        exchange.close();
    }

    private static URI observedUri(HttpExchange exchange) {
        var target = exchange.getRequestURI();
        if (target.isAbsolute()) {
            return target;
        }
        var host = exchange.getRequestHeaders().getFirst("Host");
        var path = target.getRawPath() == null ? "/" : target.getRawPath();
        var query = target.getRawQuery();
        return URI.create("http://" + host + path + (query == null ? "" : "?" + query));
    }

    private record StubResponse(int status, Map<String, List<String>> headers, byte[] body) {}
}
