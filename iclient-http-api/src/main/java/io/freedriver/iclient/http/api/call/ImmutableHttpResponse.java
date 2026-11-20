package io.freedriver.iclient.http.api.call;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ImmutableHttpResponse(int statusCode, Map<String, List<String>> headers, HttpBody body)
        implements HttpResponse {
    public ImmutableHttpResponse {
        headers = Headers.snapshot(headers);
        body = body == null ? HttpBody.empty() : body;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof ImmutableHttpResponse other && statusCode == other.statusCode
                && headers.equals(other.headers) && body == other.body;
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, headers.size(), System.identityHashCode(body));
    }

    @Override
    public String toString() {
        return "ImmutableHttpResponse[contentLength=" + body.contentLength()
                + ", contentType=" + body.contentType() + "]";
    }

    public static ImmutableHttpResponse of(HttpResponse source) {
        return new ImmutableHttpResponse(source.statusCode(), source.headers(), source.body());
    }
}
