package io.freedriver.iclient.http.api.exception;

import io.freedriver.iclient.http.api.call.ImmutableHttpRequest;
import io.freedriver.iclient.http.api.call.ImmutableHttpResponse;
import java.util.List;
import java.util.Map;

/** Request and response details carried by an HTTP failure. */
public interface HttpAdvice {
    ImmutableHttpRequest request();
    ImmutableHttpResponse response();

    default int statusCode() {
        return response().statusCode();
    }

    default Map<String, List<String>> headers() {
        return response().headers();
    }
}
