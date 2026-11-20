package io.freedriver.iclient.api;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import java.lang.reflect.InvocationHandler;
import java.util.Optional;

/**
 * Wraps an inner client, or declines an interface by returning empty. Smaller orders are
 * further out. Handlers call method.invoke(inner, args), including for Object methods, and
 * rethrow the cause of InvocationTargetException unchanged. They retain no per-call state.
 */
public interface IClientLayerFactory {
    int order();

    <P> Optional<InvocationHandler> wrap(Class<P> apiClass, P inner, IClientConfig config,
            IClientModifier... modifiers) throws IClientConstructionException;
}
