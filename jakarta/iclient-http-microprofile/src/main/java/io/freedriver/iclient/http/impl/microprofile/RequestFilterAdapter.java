package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.api.IClientContext;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import io.freedriver.iclient.http.api.modifiers.IClientHttpRequestFilter;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.util.List;

/**
 * Runs the composed IClient filters on the caller's thread and writes the method, URI and headers
 * they leave back onto the JAX-RS request. The entity is not copied into the filter request: a
 * replaced body is a different {@link io.freedriver.iclient.http.api.call.HttpBody} instance, and
 * that call is rejected before anything is sent.
 */
final class RequestFilterAdapter implements ClientRequestFilter {
    static final String FILTER_FAILED = "HTTP request filter failed";
    static final String BODY_REPLACED = "HTTP request filter replaced the body";
    static final String NO_CONTEXT = "HTTP call has no IClient context";

    private final IClientHttpRequestFilter filters;

    RequestFilterAdapter(IClientHttpRequestFilter filters) {
        this.filters = filters;
    }

    @Override
    public void filter(ClientRequestContext context) throws IOException {
        var state = CallState.current();
        var call = IClientContext.current().orElse(null);
        if (state == null || call == null) {
            throw new IClientCallException(NO_CONTEXT, null);
        }
        var request = new MutableHttpRequest();
        request.setMethod(context.getMethod());
        request.setUri(context.getUri());
        context.getStringHeaders().forEach((name, values) -> request.headers().put(name, List.copyOf(values)));
        var bodyBefore = request.body();
        try {
            filters.filter(request, call);
        } catch (IOException failure) {
            state.filterFailure = failure;
            throw failure;
        }
        if (request.body() != bodyBefore) {
            var rejected = new IllegalStateException(BODY_REPLACED);
            state.bodyRejected = rejected;
            throw rejected;
        }
        context.setMethod(request.method());
        context.setUri(request.uri());
        var outbound = context.getHeaders();
        outbound.clear();
        request.headers().forEach((name, values) -> values.forEach(value -> outbound.add(name, value)));
        state.sent = request.toImmutable();
    }
}
