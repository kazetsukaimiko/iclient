package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.http.api.call.HttpBody;
import io.freedriver.iclient.http.api.call.ImmutableHttpResponse;
import io.freedriver.iclient.http.api.exception.IClientHttpCallException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Turns status 400 and above into {@link IClientHttpCallException}. The response filter runs
 * before the implementation's mapper, which would buffer the entity, and the exception constructor
 * is what reads the stream.
 */
final class HttpStatusMapper implements ClientResponseFilter, ResponseExceptionMapper<RuntimeException> {
    /**
     * The module test wraps the entity stream here to count how many bytes the exception reads.
     * Production leaves it as identity.
     */
    static volatile UnaryOperator<InputStream> entityStreamView = UnaryOperator.identity();

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        if (response.getStatus() < 400) {
            return;
        }
        throw mapped(response.getStatus(), response.getHeaders(), response.getMediaType(), response.getEntityStream());
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }

    @Override
    public int getPriority() {
        return ResponseExceptionMapper.DEFAULT_PRIORITY;
    }

    @Override
    public RuntimeException toThrowable(Response response) {
        Object entity = response.getEntity();
        InputStream raw = entity instanceof InputStream stream ? stream : InputStream.nullInputStream();
        return mapped(response.getStatus(), response.getStringHeaders(), response.getMediaType(), raw);
    }

    private static RuntimeException mapped(int status, MultivaluedMap<String, String> headers,
            jakarta.ws.rs.core.MediaType mediaType, InputStream raw) {
        var httpResponse = unread(status, headers, mediaType, raw);
        var state = CallState.current();
        if (state != null && state.sent != null) {
            return new IClientHttpCallException(state.sent, httpResponse);
        }
        return new UnattachedHttpFailure(httpResponse);
    }

    private static ImmutableHttpResponse unread(int status, MultivaluedMap<String, String> headers,
            jakarta.ws.rs.core.MediaType mediaType, InputStream raw) {
        InputStream source = raw == null ? InputStream.nullInputStream() : raw;
        InputStream view = entityStreamView.apply(source);
        var type = Optional.ofNullable(mediaType).map(Object::toString);
        var body = HttpBody.ofStream(() -> view, OptionalLong.empty(), type, false);
        var copied = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> copied.put(name, List.copyOf(values)));
        return new ImmutableHttpResponse(status, copied, body);
    }

    /** Carries a response mapped off the caller's thread back to {@link MicroProfileClient}. */
    static final class UnattachedHttpFailure extends RuntimeException {
        final ImmutableHttpResponse response;

        UnattachedHttpFailure(ImmutableHttpResponse response) {
            super("HTTP response was mapped off the calling thread");
            this.response = response;
        }
    }
}
