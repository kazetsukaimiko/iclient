package io.freedriver.iclient.http.api.call;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record ImmutableHttpRequest(String method, URI uri, Map<String, List<String>> headers)
        implements HttpRequest {
    public ImmutableHttpRequest {
        headers = Headers.snapshot(headers);
    }

    public static ImmutableHttpRequest of(HttpRequest source) {
        return new ImmutableHttpRequest(source.method(), source.uri(), source.headers());
    }
}
