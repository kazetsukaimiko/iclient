package io.freedriver.iclient.http.tck;

import io.freedriver.iclient.api.IClient;
import io.freedriver.iclient.api.IClientConfig;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.api.IClientVendor;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.http.api.call.HttpBody;
import io.freedriver.iclient.http.api.call.ImmutableHttpRequest;
import io.freedriver.iclient.http.api.config.HttpClientConfig;
import io.freedriver.iclient.http.api.exception.IClientHttpCallException;
import io.freedriver.iclient.http.api.modifiers.HttpHeadersModifier;
import io.freedriver.iclient.http.api.modifiers.IClientHttpRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract of an IClient HTTP vendor. A vendor module extends this class in its tests and
 * implements the seam below; the inherited tests then run in that module's {@code mvn verify}.
 *
 * <p>The vendor under test is registered as an {@link IClientVendor} service in the test runtime,
 * because the tests build their clients through {@link IClient#create}. The seam replaces the wire:
 * it answers each request with the stubbed response and records the request the vendor sent.
 *
 * @param <P> the caller interface the vendor's tests supply, in the form that vendor reads
 */
public abstract class HttpVendorContract<P> {
    private static final String ORDER = "X-Kit-Order";

    /** The vendor under test, for the cases that call it directly. */
    protected abstract IClientVendor vendor();

    /** A caller interface this vendor supports. */
    protected abstract Class<P> apiClass();

    /** A config whose base URI reaches the stub. */
    protected abstract HttpClientConfig config();

    /** Makes one call on the client and lets any failure propagate unchanged. */
    protected abstract void call(P client);

    /** Sets the response to every following request and forgets the request recorded so far. */
    protected abstract void stub(int status, Map<String, List<String>> headers, byte[] body);

    /**
     * The request most recently sent since the last {@link #stub}, as it left the vendor after the
     * filters ran. Empty when the vendor sent nothing.
     */
    protected abstract Optional<ImmutableHttpRequest> sentRequest();

    @Test
    public void filtersRunInModifierOrderOnEveryCall() throws IClientConstructionException {
        var runs = new ArrayList<String>();
        IClientHttpRequestFilter first = (request, call) -> {
            assertNotNull(call);
            runs.add("first");
            request.headers().put(ORDER, List.of("first"));
        };
        IClientHttpRequestFilter second = (request, call) -> {
            runs.add("second");
            request.headers().put(ORDER, List.of(request.headers().get(ORDER).get(0) + "-second"));
        };
        var client = client(first, second);
        for (int calls = 1; calls <= 2; calls++) {
            stub(200, Map.of(), new byte[0]);
            call(client);
            assertEquals(List.of("first-second"), sent().headers().get(ORDER));
            assertEquals(calls * 2, runs.size());
        }
        assertEquals(List.of("first", "second", "first", "second"), runs);
    }

    @Test
    public void methodAndUriChangesByAFilterAreSent() throws IClientConstructionException {
        var uri = URI.create(config().baseUri().toString().replaceAll("/$", "") + "/kit-filtered");
        var client = client((IClientHttpRequestFilter) (request, call) -> {
            request.setMethod("DELETE");
            request.setUri(uri);
        });
        stub(200, Map.of(), new byte[0]);
        call(client);
        assertEquals("DELETE", sent().method());
        assertEquals(uri, sent().uri());
    }

    @Test
    public void headersModifierHeaderIsSent() throws IClientConstructionException {
        var client = client(new HttpHeadersModifier(Map.of("X-Kit-Header", "kit-value")));
        stub(200, Map.of(), new byte[0]);
        call(client);
        assertEquals(List.of("kit-value"), sent().headers().get("x-kit-header"));
    }

    @Test
    public void bodyChangeFailsTheCallAndSendsNothing() throws IClientConstructionException {
        var client = client((IClientHttpRequestFilter) (request, call) ->
                request.setBody(HttpBody.ofBytes(new byte[]{1}, "application/octet-stream")));
        stub(200, Map.of(), new byte[0]);
        var failure = assertThrows(IClientCallException.class, () -> call(client));
        assertNotNull(failure.getCause());
        assertFalse(sentRequest().isPresent());
    }

    @Test
    public void filterIoExceptionFailsTheCallAndSendsNothing() throws IClientConstructionException {
        var error = new IOException("filter failed");
        var client = client((IClientHttpRequestFilter) (request, call) -> {
            throw error;
        });
        stub(200, Map.of(), new byte[0]);
        var failure = assertThrows(IClientCallException.class, () -> call(client));
        assertSame(error, failure.getCause());
        assertFalse(sentRequest().isPresent());
    }

    @Test
    public void rawClientWithNoContextFailsTheCallBeforeItsFilters() throws IClientConstructionException {
        var runs = new ArrayList<String>();
        IClientHttpRequestFilter filter = (request, call) -> runs.add("ran");
        var raw = vendor().createClient(apiClass(), config(), filter).orElseThrow();
        stub(200, Map.of(), new byte[0]);
        assertThrows(IClientCallException.class, () -> call(raw));
        assertEquals(List.of(), runs);
        assertFalse(sentRequest().isPresent());
    }

    @Test
    public void statusOf400OrAboveIsAnHttpCallException() throws IClientConstructionException {
        var client = client((IClientHttpRequestFilter) (request, call) ->
                request.headers().put(ORDER, List.of("filtered")));
        byte[] body = "kit failure body".getBytes(StandardCharsets.UTF_8);
        for (int status : new int[]{400, 404, 503}) {
            stub(status, Map.of("X-Kit-Response", List.of("stubbed")), body);
            var failure = assertThrows(IClientHttpCallException.class, () -> call(client));
            assertEquals(status, failure.statusCode());
            assertEquals(List.of("stubbed"), failure.headers().get("x-kit-response"));
            assertArrayEquals(body, read(failure.response().body()));
            assertFalse(failure.bodyTruncated());
            assertEquals(sent().method(), failure.request().method());
            assertEquals(sent().uri(), failure.request().uri());
            assertEquals(List.of("filtered"), failure.request().headers().get(ORDER));
        }
    }

    @Test
    public void failureBodyIsCapturedUpToTheLimit() throws IClientConstructionException {
        byte[] body = new byte[IClientHttpCallException.CAPTURE_LIMIT + 10];
        Arrays.fill(body, (byte) 'k');
        stub(500, Map.of(), body);
        var client = client();
        var failure = assertThrows(IClientHttpCallException.class, () -> call(client));
        assertTrue(failure.bodyTruncated());
        assertArrayEquals(Arrays.copyOf(body, IClientHttpCallException.CAPTURE_LIMIT), read(failure.response().body()));
    }

    @Test
    public void vendorDeclinesAConfigThatIsNotHttp() throws IClientConstructionException {
        assertEquals(Optional.empty(), vendor().createClient(apiClass(), new IClientConfig() {}));
    }

    private P client(IClientModifier... modifiers) throws IClientConstructionException {
        return IClient.create(apiClass(), config(), modifiers);
    }

    private ImmutableHttpRequest sent() {
        return sentRequest().orElseThrow(() -> new AssertionError("The vendor sent no request"));
    }

    private static byte[] read(HttpBody body) {
        try (var stream = body.openStream()) {
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
