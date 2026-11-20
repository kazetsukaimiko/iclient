package io.freedriver.iclient.http.api.call;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Per-call builder. Its live header map is mutable and is not thread-safe. */
public final class MutableHttpRequest implements HttpRequest {
    private String method;
    private URI uri;
    private final Map<String, List<String>> headers = Headers.mutable();

    @Override
    public String method() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public URI uri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
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

    public ImmutableHttpRequest toImmutable() {
        return ImmutableHttpRequest.of(this);
    }
}
