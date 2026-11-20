package io.freedriver.iclient.validation.api;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.validation.api.exception.InvalidArgumentsCallException;
import io.freedriver.iclient.validation.api.exception.InvalidReturnCallException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationLayerTest {
    public interface Api { String marked(String arg); String plain(); }
    public interface Plain { String plain(); }
    public static class Calls implements MethodCallValidator {
        public List<Violation> validate(Method method, Object[] args) {
            return args.length > 0 && args[0] == null ? List.of(new Violation("arg0", "secret", "C")) : List.of();
        }
        public boolean validatesReturnValue(Method method) { return method.getName().equals("marked"); }
    }
    public static class OtherCalls extends Calls {}
    public static class Objects implements ObjectValidator {
        public List<Violation> validate(Object result) { return List.of(new Violation("field", "secret", "C")); }
    }

    @TempDir Path directory;

    private void register(Class<?> service, Class<?>... providers) throws Exception {
        var file = directory.resolve("META-INF/services").resolve(service.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, java.util.Arrays.stream(providers).map(Class::getName).toList());
    }

    @Test
    void validatesOnlyApplicableCallsAndReportsDuplicateProviders() throws Exception {
        var factory = new ValidationLayerFactory();
        var raw = new Api() {
            public String marked(String arg) { return java.util.Objects.requireNonNull(arg); }
            public String plain() { return "ok"; }
        };
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        try (var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, original)) {
            thread.setContextClassLoader(loader);
            assertTrue(factory.wrap(Api.class, raw, null).isEmpty());
            register(MethodCallValidator.class, Calls.class);
            register(ObjectValidator.class, Objects.class);
            assertTrue(factory.wrap(Plain.class, () -> "ok", null).isEmpty());
            var handler = factory.wrap(Api.class, raw, null).orElseThrow();
            var client = (Api) Proxy.newProxyInstance(loader, new Class<?>[]{Api.class}, handler);
            assertEquals("ok", client.plain());
            var args = assertThrows(InvalidArgumentsCallException.class, () -> client.marked(null));
            assertEquals("1 violation(s): arg0", args.getMessage());
            var result = assertThrows(InvalidReturnCallException.class, () -> client.marked("secret"));
            assertEquals("1 violation(s): return.field", result.getMessage());
            assertEquals("secret", result.returnValue(String.class));
            register(MethodCallValidator.class, Calls.class, OtherCalls.class);
            var duplicate = assertThrows(IClientConstructionException.class, () -> factory.wrap(Api.class, raw, null));
            assertTrue(duplicate.getMessage().contains(OtherCalls.class.getName()));
            assertTrue(duplicate.getMessage().contains(Calls.class.getName()));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
