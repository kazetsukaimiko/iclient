package io.freedriver.iclient.validation.api;

import io.freedriver.iclient.api.IClientConfig;
import io.freedriver.iclient.api.IClientLayerFactory;
import io.freedriver.iclient.api.IClientLayers;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.validation.api.exception.InvalidArgumentsCallException;
import io.freedriver.iclient.validation.api.exception.InvalidReturnCallException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.ServiceLoader;

/** Installs argument and return validation only for interfaces with marked calls. */
public final class ValidationLayerFactory implements IClientLayerFactory {
    public int order() {
        return IClientLayers.VALIDATION;
    }

    public <P> Optional<InvocationHandler> wrap(Class<P> apiClass, P inner, IClientConfig config,
            IClientModifier... modifiers) throws IClientConstructionException {
        var calls = single(MethodCallValidator.class);
        var objects = single(ObjectValidator.class);
        if (calls == null || objects == null || Arrays.stream(apiClass.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .noneMatch(method -> calls.validatesArguments(method) || calls.validatesReturnValue(method))) {
            return Optional.empty();
        }
        return Optional.of((proxy, method, args) -> {
            boolean clientMethod = method.getDeclaringClass() != Object.class;
            if (clientMethod) {
                var failures = calls.validate(method, args == null ? new Object[0] : args);
                if (!failures.isEmpty()) {
                    throw new InvalidArgumentsCallException(failures);
                }
            } else if (method.getName().equals("equals") && args[0] == proxy) {
                args = new Object[]{inner};
            }
            Object result;
            try {
                if (!method.canAccess(inner)) {
                    method.trySetAccessible();
                }
                result = method.invoke(inner, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            if (clientMethod && result != null && calls.validatesReturnValue(method)) {
                var failures = objects.validate(result).stream().map(v -> new Violation(
                        v.path().isEmpty() ? "return" : "return." + v.path(), v.message(), v.constraint())).toList();
                if (!failures.isEmpty()) {
                    throw new InvalidReturnCallException(failures, result);
                }
            }
            return result;
        });
    }

    private static <T> T single(Class<T> service) throws IClientConstructionException {
        var providers = ServiceLoader.load(service).stream().toList();
        if (providers.size() > 1) {
            throw new IClientConstructionException("Multiple " + service.getSimpleName() + " providers: "
                    + providers.stream().map(provider -> provider.type().getName()).toList());
        }
        return providers.isEmpty() ? null : providers.get(0).get();
    }
}
