package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientContext;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import java.io.IOException;

/**
 * Changes an HTTP request before it is sent.
 *
 * <p>An HTTP vendor applies every filter among a client's modifiers on every call, in modifier order,
 * after it builds the request and before it sends. Each filter sees the changes of the filters before it.
 * The method, URI and headers a filter leaves on the request are what the vendor sends.
 *
 * <p>The call fails, and the vendor sends nothing, when a filter replaces the request body or throws
 * {@link IOException}. The caller receives an
 * {@link io.freedriver.iclient.api.exception.IClientCallException} (or a subclass) whose cause is the
 * filter's {@code IOException} or the vendor's rejection of the body change.
 *
 * <p>{@code call} is the context {@link io.freedriver.iclient.api.IClient#create} installs for the call
 * and is never null. A vendor client that runs with no context fails the call with an
 * {@code IClientCallException} before it invokes a filter.
 */
@FunctionalInterface
public interface IClientHttpRequestFilter extends IClientModifier {
    void filter(MutableHttpRequest request, IClientContext call) throws IOException;
}
