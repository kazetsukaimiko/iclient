package io.freedriver.iclient.api;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ServiceLoader;

/** Builds a raw vendor client and wraps it in applicable layers, highest order first. */
public final class IClient {
    private IClient() {}

    public static <P> P create(Class<P> apiClass, IClientConfig config, IClientModifier... modifiers)
            throws IClientConstructionException {
        if (!apiClass.isInterface()) {
            throw new IClientConstructionException(apiClass.getName() + " is not an interface");
        }
        var expanded = Arrays.stream(modifiers).flatMap(IClientModifier::upgrade).toArray(IClientModifier[]::new);
        P inner = rawClient(apiClass, config, expanded);
        var layers = ServiceLoader.load(IClientLayerFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(IClientLayerFactory::order).reversed()).toList();
        for (var layer : layers) {
            var handler = layer.wrap(apiClass, inner, config, expanded.clone());
            if (handler.isPresent()) {
                inner = proxy(apiClass, handler.get());
            }
        }
        return inner;
    }

    private static <P> P rawClient(Class<P> apiClass, IClientConfig config, IClientModifier[] modifiers)
            throws IClientConstructionException {
        for (var vendor : ServiceLoader.load(IClientVendor.class)) {
            var client = vendor.createClient(apiClass, config, modifiers.clone());
            if (client.isPresent()) {
                return client.get();
            }
        }
        return proxy(apiClass, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> apiClass.getName();
                };
            }
            throw new UnsupportedOperationException("No IClientVendor for " + apiClass.getName() + "." + method.getName());
        });
    }

    private static <P> P proxy(Class<P> apiClass, InvocationHandler handler) {
        return apiClass.cast(Proxy.newProxyInstance(apiClass.getClassLoader(), new Class<?>[]{apiClass}, handler));
    }
}
