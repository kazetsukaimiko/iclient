package io.freedriver.iclient.http.api.call;

import io.freedriver.iclient.http.api.exception.HttpAdvice;
import io.freedriver.iclient.http.api.exception.IClientHttpCallException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpContractTest {
    private static final URI URI_VALUE = URI.create("https://example.org/resource");
    private static final int LIMIT = IClientHttpCallException.CAPTURE_LIMIT;
    private static final ImmutableHttpRequest REQUEST = new ImmutableHttpRequest(
            "GET", URI_VALUE, Map.of("Authorization", List.of("private")), null);

    @Test
    void envelopesSnapshotHeadersAndKeepTheBodySource() {
        var values = new ArrayList<>(List.of("old"));
        var headers = new HashMap<String, List<String>>(Map.of("Example", values));
        var mutable = new MutableHttpRequest();
        mutable.setMethod("PUT");
        mutable.setUri(URI_VALUE);
        mutable.headers().putAll(headers);
        var body = HttpBody.ofBytes(new byte[]{1}, "image/png");
        mutable.setBody(body);
        var request = mutable.toImmutable();
        var response = new ImmutableHttpResponse(200, headers, null);
        values.clear();
        mutable.headers().clear();
        headers.clear();
        assertEquals(List.of("old"), request.headers().get("EXAMPLE"));
        assertEquals(List.of("old"), response.headers().get("example"));
        assertThrows(UnsupportedOperationException.class, () -> request.headers().clear());
        assertThrows(UnsupportedOperationException.class, () -> response.headers().get("example").clear());
        assertSame(body, request.body());
        assertEquals("PUT", request.method());
        assertEquals(URI_VALUE, request.uri());
        assertSame(HttpBody.empty(), response.body());
        assertEquals(request, ImmutableHttpRequest.of(request));
    }

    @Test
    void bytesCopyAndReplay() throws IOException {
        byte[] bytes = {1, 2};
        var body = HttpBody.ofBytes(bytes, "image/png");
        bytes[0] = 3;
        assertTrue(body.repeatable());
        assertEquals(Optional.of("image/png"), body.contentType());
        assertEquals(OptionalLong.of(2), body.contentLength());
        assertArrayEquals(new byte[]{1, 2}, read(body));
        assertArrayEquals(new byte[]{1, 2}, read(body));
    }

    private static byte[] read(HttpBody body) throws IOException {
        try (var stream = body.openStream()) {
            return stream.readAllBytes();
        }
    }

    @Test
    void capturesOnlyPrefixAndReplaysItEvenWithIncorrectAdvertisedLength() throws IOException {
        byte[] bytes = new byte[LIMIT + 10];
        Arrays.fill(bytes, (byte) 's');
        var opens = new AtomicInteger();
        var input = new ByteArrayInputStream(bytes);
        var body = HttpBody.ofStream(() -> {
            assertEquals(1, opens.incrementAndGet());
            return input;
        }, OptionalLong.of(2), Optional.of("text/plain"), false);
        assertFalse(body.repeatable());
        var request = new ImmutableHttpRequest("POST", URI_VALUE, REQUEST.headers(),
                HttpBody.ofStream(() -> { throw new AssertionError("request body opened"); },
                        OptionalLong.empty(), Optional.empty(), false));
        var response = new ImmutableHttpResponse(503, Map.of("Secret", List.of("private")), body);
        var failure = new IClientHttpCallException(request, response);
        HttpAdvice advice = failure;
        assertSame(request, advice.request());
        assertEquals(503, advice.statusCode());
        assertEquals(List.of("private"), advice.headers().get("secret"));
        assertEquals("POST " + URI_VALUE + " -> 503", failure.getMessage());
        assertTrue(failure.bodyTruncated());
        assertEquals(9, input.available());
        var captured = advice.response().body();
        assertTrue(captured.repeatable());
        assertEquals(body.contentType(), captured.contentType());
        assertArrayEquals(Arrays.copyOf(bytes, LIMIT), read(captured));
        assertArrayEquals(Arrays.copyOf(bytes, LIMIT), read(captured));
    }

    @Test
    void errorMessageOmitsUserInfoQueryAndFragment() {
        var uri = URI.create("https://user:password@example.org:8443/a%2Fb?token=secret#private");
        var request = new ImmutableHttpRequest("GET", uri, Map.of(), null);
        var response = new ImmutableHttpResponse(403, Map.of(), null);
        var failure = new IClientHttpCallException(request, response);
        assertEquals("GET https://example.org:8443/a%2Fb -> 403", failure.getMessage());
    }

    @Test
    void exactLimitIsComplete() {
        var response = new ImmutableHttpResponse(400, Map.of(), HttpBody.ofBytes(new byte[LIMIT], null));
        assertFalse(new IClientHttpCallException(REQUEST, response).bodyTruncated());
    }

    @Test
    void midstreamFailureKeepsPrefixAndClosesStream() throws IOException {
        var error = new IOException("stream failed");
        var closed = new AtomicInteger();
        var body = HttpBody.ofStream(() -> new InputStream() {
            boolean first = true;
            public int read() throws IOException {
                if (first) {
                    first = false;
                    return 42;
                }
                throw error;
            }
            public void close() {
                closed.incrementAndGet();
            }
        }, OptionalLong.empty(), Optional.empty(), false);
        var failure = new IClientHttpCallException(REQUEST, new ImmutableHttpResponse(400, Map.of(), body));
        assertTrue(failure.bodyTruncated());
        assertSame(error, failure.getSuppressed()[0]);
        assertEquals(1, closed.get());
        assertArrayEquals(new byte[]{42}, read(failure.response().body()));
    }

    @Test
    void retryAdviceMatchesTheSixSpecifiedStatusCodes() {
        var retryable = Set.of(408, 425, 429, 502, 503, 504);
        for (int status = 100; status <= 599; status++) {
            var response = new ImmutableHttpResponse(status, Map.of(), null);
            assertEquals(retryable.contains(status), new IClientHttpCallException(REQUEST, response).isRetryable());
        }
    }
}
