package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientContext;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Headers set on every request. Supply comma-joined values for list-valued headers.
 *
 * <p>The modifier expands into one {@link IClientHttpRequestFilter} that sets each named header to its
 * single value, replacing the values the request had under that name in any letter case. Header names
 * are case-insensitive, so two names that differ only in case are rejected. {@link #toString()} lists
 * the header names and omits their values.
 */
public record HttpHeadersModifier(Map<String, String> headers) implements IClientModifier {
    public HttpHeadersModifier {
        var copy = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> {
            if (name == null || value == null) {
                throw new IllegalArgumentException("Header names and values must be present");
            }
            if (copy.put(name, value) != null) {
                throw new IllegalArgumentException("Header named more than once: " + name);
            }
        });
        headers = Collections.unmodifiableMap(copy);
    }

    @Override
    public Stream<IClientModifier> upgrade() {
        return Stream.of(new ReplaceHeaders(headers));
    }

    @Override
    public String toString() {
        return "HttpHeadersModifier[names=" + headers.keySet() + "]";
    }

    private record ReplaceHeaders(Map<String, String> headers) implements IClientHttpRequestFilter {
        @Override
        public void filter(MutableHttpRequest request, IClientContext call) {
            headers.forEach((name, value) -> request.headers().put(name, new ArrayList<>(List.of(value))));
        }

        @Override
        public String toString() {
            return "HttpHeadersFilter[names=" + headers.keySet() + "]";
        }
    }
}
