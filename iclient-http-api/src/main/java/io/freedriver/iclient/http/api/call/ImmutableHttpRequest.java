package io.freedriver.iclient.http.api.call;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ImmutableHttpRequest(String method, URI uri, Map<String, List<String>> headers, HttpBody body)
        implements HttpRequest {
    public ImmutableHttpRequest {
        headers = Headers.snapshot(headers);
        body = body == null ? HttpBody.empty() : body;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof ImmutableHttpRequest other && Objects.equals(method, other.method) && Objects.equals(uri, other.uri)
                && headers.equals(other.headers) && body == other.body;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, uri, headers.size(), System.identityHashCode(body));
    }

    @Override
    public String toString() {
        return "ImmutableHttpRequest[contentLength=" + body.contentLength()
                + ", contentType=" + body.contentType() + "]";
    }

    public static ImmutableHttpRequest of(HttpRequest source) {
        return new ImmutableHttpRequest(source.method(), source.uri(), source.headers(), source.body());
    }
}
