package io.freedriver.iclient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProxyContractTest {
    interface Parent {
        String value();
    }

    interface Client extends Parent {
        default String greeting() {
            return "hello " + value();
        }

        void fail();
    }

    interface Incomplete extends Client {
        void missing();
    }

    private final IClientCallException failure = new IClientCallException("call failed");
    private final IClientHandlerFactory handlers = (type, method, config, modifiers) ->
            switch (method.getName()) {
                case "value" -> Optional.of((proxy, called, args) -> "world");
                case "fail" -> Optional.of((proxy, called, args) -> { throw failure; });
                default -> Optional.empty();
            };

    private record TestFactory(IClientHandlerFactory factory) implements IClientProxyFactory {
        @Override
        public <T> T create(Class<T> type, IClientConfig config, IClientModifier... modifiers)
                throws IClientConstructionException {
            if (!type.isInterface()) {
                throw new IClientConstructionException(type.getName());
            }
            var expanded = Arrays.stream(modifiers).flatMap(IClientModifier::upgrade)
                    .toArray(IClientModifier[]::new);
            var resolved = new HashMap<Method, InvocationHandler>();
            for (Method method : type.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || objectMethod(method)) {
                    continue;
                }
                var handler = factory.create(type, method, config, expanded);
                if (handler.isPresent()) {
                    resolved.put(method, handler.get());
                } else if (!method.isDefault()) {
                    throw new IClientConstructionException(method.toGenericString());
                }
            }
            var bindings = Map.copyOf(resolved);
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                default -> type.getName();
                            };
                        }
                        var handler = bindings.get(method);
                        return handler == null ? InvocationHandler.invokeDefault(proxy, method, args)
                                : handler.invoke(proxy, method, args);
                    }));
        }

        private static boolean objectMethod(Method method) {
            try {
                return Object.class.getMethod(method.getName(), method.getParameterTypes()) != null;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        }
    }

    private final TestFactory factory = new TestFactory(handlers);
    private final IClientConfig config = new IClientConfig() {};

    @Test
    void inheritedMethodReturnsHandlerValue() throws IClientConstructionException {
        assertEquals("world", factory.create(Client.class, config).value());
    }

    @Test
    void missingMethodFailsDuringConstruction() {
        var error = assertThrows(IClientConstructionException.class,
                () -> factory.create(Incomplete.class, config));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void unclaimedDefaultExecutesItsBody() throws IClientConstructionException {
        assertEquals("hello world", factory.create(Client.class, config).greeting());
    }

    @Test
    void callFailureReachesCallerUnwrapped() throws IClientConstructionException {
        var client = factory.create(Client.class, config);
        assertSame(failure, assertThrows(IClientCallException.class, client::fail));
    }
}
