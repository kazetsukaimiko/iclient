package io.freedriver.iclient.api;

import io.freedriver.iclient.api.annotation.MapExceptions;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.api.exception.IClientExceptionMapper;
import io.freedriver.iclient.api.ext.BeanCreator;
import io.freedriver.iclient.api.ext.NoArgBeanCreator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** Applies the mapper selected at client construction to failures from the raw client. */
public final class ExceptionMappingLayerFactory implements IClientLayerFactory {
    public int order() {
        return IClientLayers.EXCEPTION_MAPPING;
    }

    public <P> Optional<InvocationHandler> wrap(Class<P> apiClass, P inner, IClientConfig config,
            IClientModifier... modifiers) throws IClientConstructionException {
        var instances = new HashMap<Class<? extends IClientExceptionMapper>, IClientExceptionMapper>();
        var methods = new HashMap<Method, IClientExceptionMapper>();
        for (var method : apiClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            var annotation = method.getAnnotation(MapExceptions.class);
            if (annotation == null) {
                annotation = nearest(apiClass, method.getDeclaringClass());
            }
            if (annotation != null) {
                var type = annotation.using();
                if (!instances.containsKey(type)) {
                    instances.put(type, createMapper(type));
                }
                methods.put(method, instances.get(type));
            }
        }
        if (methods.isEmpty()) {
            return Optional.empty();
        }
        var bindings = Map.copyOf(methods);
        return Optional.of((proxy, method, args) -> {
            try {
                if (method.getDeclaringClass() == Object.class && method.getName().equals("equals") && args[0] == proxy) {
                    args = new Object[]{inner};
                }
                if (!method.canAccess(inner)) {
                    method.trySetAccessible();
                }
                return method.invoke(inner, args);
            } catch (InvocationTargetException failure) {
                var cause = failure.getCause();
                var mapper = bindings.get(method);
                if (cause instanceof IClientCallException original && mapper != null) {
                    throw map(method, mapper, original);
                }
                throw cause;
            }
        });
    }

    private static MapExceptions nearest(Class<?> apiClass, Class<?> declaring) {
        var queue = new ArrayDeque<Class<?>>();
        var seen = new HashSet<Class<?>>();
        queue.add(declaring);
        if (declaring != apiClass && !declaring.isAnnotationPresent(MapExceptions.class)
                && apiClass.isAnnotationPresent(MapExceptions.class)) {
            return apiClass.getAnnotation(MapExceptions.class);
        }
        while (!queue.isEmpty()) {
            var type = queue.remove();
            if (seen.add(type)) {
                var annotation = type.getAnnotation(MapExceptions.class);
                if (annotation != null) {
                    return annotation;
                }
                queue.addAll(List.of(type.getInterfaces()));
            }
        }
        return null;
    }

    private static IClientExceptionMapper createMapper(Class<? extends IClientExceptionMapper> type)
            throws IClientConstructionException {
        try {
            for (var creator : ServiceLoader.load(BeanCreator.class)) {
                if (!(creator instanceof NoArgBeanCreator)) {
                    var mapper = creator.create(type);
                    if (mapper.isPresent()) {
                        return mapper.get();
                    }
                }
            }
            return new NoArgBeanCreator().create(type).orElseThrow(
                    () -> new IClientConstructionException("Cannot create mapper " + type.getName()));
        } catch (RuntimeException failure) {
            throw new IClientConstructionException("Cannot create mapper " + type.getName(), failure);
        }
    }

    private static Throwable map(Method method, IClientExceptionMapper mapper, IClientCallException original) {
        Throwable mapped;
        try {
            mapped = mapper.map(original).orElse(original);
        } catch (Throwable failure) {
            if (failure != original) {
                original.addSuppressed(failure);
            }
            return original;
        }
        if (mapped instanceof RuntimeException || mapped instanceof Error
                || Arrays.stream(method.getExceptionTypes()).anyMatch(type -> type.isInstance(mapped))) {
            return mapped;
        }
        original.addSuppressed(mapped);
        return original;
    }
}
