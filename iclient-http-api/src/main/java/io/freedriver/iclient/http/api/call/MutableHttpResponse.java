package io.freedriver.iclient.http.api.call;

import java.util.List;
import java.util.Map;

/** Per-call builder. Its live header map is mutable and is not thread-safe. */
public final class MutableHttpResponse implements HttpResponse {
    private int statusCode;
    private final Map<String, List<String>> headers = Headers.mutable();

    @Override
    public int statusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public Map<String, List<String>> headers() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        var copy = Headers.snapshot(headers);
        this.headers.clear();
        this.headers.putAll(copy);
    }

    public ImmutableHttpResponse toImmutable() {
        return ImmutableHttpResponse.of(this);
    }
}
