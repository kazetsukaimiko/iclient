package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientModifier;
import java.util.Map;

/** Headers added to every request. Supply comma-joined values for list-valued headers. */
public record HttpHeadersModifier(Map<String, String> headers) implements IClientModifier {
    public HttpHeadersModifier {
        headers = Map.copyOf(headers);
    }
}
