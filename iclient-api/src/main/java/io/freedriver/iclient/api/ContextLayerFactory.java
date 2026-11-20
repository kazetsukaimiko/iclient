package io.freedriver.iclient.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Makes context available only for the duration of a synchronous client call. */
public final class ContextLayerFactory implements IClientLayerFactory {
    public int order() {
        return IClientLayers.CONTEXT;
    }

    public <P> Optional<InvocationHandler> wrap(Class<P> apiClass, P inner, IClientConfig config,
            IClientModifier... modifiers) {
        var options = List.of(modifiers);
        return Optional.of((proxy, method, args) -> {
            var previous = IClientContext.ACTIVE.get();
            IClientContext.ACTIVE.set(new IClientContext(apiClass, method,
                    args == null ? List.of() : Arrays.asList(args), config, options));
            try {
                if (method.getDeclaringClass() == Object.class && method.getName().equals("equals") && args[0] == proxy) {
                    args = new Object[]{inner};
                }
                if (!method.canAccess(inner)) {
                    method.trySetAccessible();
                }
                return method.invoke(inner, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            } finally {
                if (previous == null) {
                    IClientContext.ACTIVE.remove();
                } else {
                    IClientContext.ACTIVE.set(previous);
                }
            }
        });
    }
}
