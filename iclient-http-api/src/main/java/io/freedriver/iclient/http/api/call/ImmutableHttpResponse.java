package io.freedriver.iclient.http.api.call;

import java.util.List;
import java.util.Map;

public record ImmutableHttpResponse(int statusCode, Map<String, List<String>> headers)
        implements HttpResponse {
    public ImmutableHttpResponse {
        headers = Headers.snapshot(headers);
    }

    public static ImmutableHttpResponse of(HttpResponse source) {
        return new ImmutableHttpResponse(source.statusCode(), source.headers());
    }
}
