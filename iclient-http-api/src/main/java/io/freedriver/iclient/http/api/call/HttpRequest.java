package io.freedriver.iclient.http.api.call;

import java.net.URI;
import java.util.List;
import java.util.Map;

public interface HttpRequest {
    String method();
    URI uri();

    /** Returns headers with case-insensitive name lookup. */
    Map<String, List<String>> headers();

    /** The body stream source, empty when absent. */
    HttpBody body();
}
