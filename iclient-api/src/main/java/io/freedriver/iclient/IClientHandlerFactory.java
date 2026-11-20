package io.freedriver.iclient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Optional;

/** SPI. Supplies the handler for one interface method, or empty when it does not handle it. */
public interface IClientHandlerFactory {
    Optional<InvocationHandler> create(Class<?> interfaceClass, Method method,
            IClientConfig config, IClientModifier... modifiers) throws IClientConstructionException;

    default Optional<InvocationHandler> create(Method method, IClientConfig config,
            IClientModifier... modifiers) throws IClientConstructionException {
        return create(method.getDeclaringClass(), method, config, modifiers);
    }
}
