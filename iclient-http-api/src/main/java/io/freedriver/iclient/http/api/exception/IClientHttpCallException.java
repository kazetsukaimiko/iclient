package io.freedriver.iclient.http.api.exception;

import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.api.exception.RetryAdvice;
import io.freedriver.iclient.http.api.call.HttpBody;
import io.freedriver.iclient.http.api.call.ImmutableHttpRequest;
import io.freedriver.iclient.http.api.call.ImmutableHttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

/** A failed HTTP exchange with a replayable, bounded prefix of the response body. */
public class IClientHttpCallException extends IClientCallException implements HttpAdvice, RetryAdvice {
    public static final int CAPTURE_LIMIT = 64 * 1024;

    private final ImmutableHttpRequest request;
    private final ImmutableHttpResponse response;
    private final boolean bodyTruncated;

    public IClientHttpCallException(ImmutableHttpRequest request, ImmutableHttpResponse response) {
        super(request.method() + " " + displayUri(request.uri()) + " -> " + response.statusCode());
        this.request = request;
        var prefix = new ByteArrayOutputStream();
        boolean truncated = false;
        try (var stream = response.body().openStream()) {
            byte[] buffer = new byte[8192];
            while (prefix.size() < CAPTURE_LIMIT) {
                int count = stream.read(buffer, 0, Math.min(buffer.length, CAPTURE_LIMIT - prefix.size()));
                if (count == -1) {
                    break;
                }
                prefix.write(buffer, 0, count);
            }
            truncated = prefix.size() == CAPTURE_LIMIT && stream.read() != -1;
        } catch (IOException failure) {
            addSuppressed(failure);
            truncated = true;
        }
        bodyTruncated = truncated;
        var captured = HttpBody.ofBytes(prefix.toByteArray(), response.body().contentType().orElse(null));
        this.response = new ImmutableHttpResponse(response.statusCode(), response.headers(), captured);
    }

    private static String displayUri(URI uri) {
        var display = new StringBuilder();
        if (uri.getScheme() != null) {
            display.append(uri.getScheme()).append(':');
        }
        if (uri.getHost() != null) {
            display.append("//").append(uri.getHost());
            if (uri.getPort() >= 0) {
                display.append(':').append(uri.getPort());
            }
        }
        if (uri.getRawPath() != null) {
            display.append(uri.getRawPath());
        }
        return display.toString();
    }

    @Override
    public ImmutableHttpRequest request() {
        return request;
    }

    @Override
    public ImmutableHttpResponse response() {
        return response;
    }

    public boolean bodyTruncated() {
        return bodyTruncated;
    }

    @Override
    public boolean isRetryable() {
        return switch (statusCode()) {
            case 408, 425, 429, 502, 503, 504 -> true;
            default -> false;
        };
    }
}
