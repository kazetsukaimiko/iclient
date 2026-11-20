package io.freedriver.iclient.http.api.call;

import java.util.List;
import java.util.Map;

public interface HttpResponse {
    int statusCode();

    /** Returns headers with case-insensitive name lookup. */
    Map<String, List<String>> headers();
}
