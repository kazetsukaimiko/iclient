package io.freedriver.iclient.api;

import io.freedriver.iclient.api.annotation.MapExceptions;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.api.exception.IClientExceptionMapper;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClientLayersTest {
    public interface Missing { void missing(); }
    public interface Client {
        void fail() throws IOException;
        String outer(Client nested);
        String inner(String nullable);
        @MapExceptions(using = Mapper.class) void mapped() throws IOException;
    }

    private static final IOException FAILURE = new IOException("raw failure");
    private static final ThreadLocal<List<Integer>> TRACE = ThreadLocal.withInitial(ArrayList::new);
    public static class Mapper implements IClientExceptionMapper {
        public Optional<Throwable> map(IClientCallException failure) {
            return Optional.of(FAILURE);
        }
    }

    public static class Vendor implements IClientVendor {
        public <P> Optional<P> createClient(Class<P> type, IClientConfig config, IClientModifier... modifiers) {
            if (type != Client.class) {
                return Optional.empty();
            }
            return Optional.of(type.cast(new Client() {
                public void fail() throws IOException { throw FAILURE; }
                public void mapped() { throw new IClientCallException(); }
                public String inner(String nullable) {
                    var context = IClientContext.current().orElseThrow();
                    assertEquals("inner", context.method().getName());
                    assertEquals(Arrays.asList((Object) null), context.args());
                    return "inner";
                }
                public String outer(Client nested) {
                    var context = IClientContext.current().orElseThrow();
                    assertEquals(Client.class, context.apiClass());
                    assertEquals(List.of(nested), context.args());
                    var result = nested.inner(null);
                    assertSame(context, IClientContext.current().orElseThrow());
                    return result;
                }
                public int hashCode() { return 99; }
                public String toString() { return "raw"; }
                public boolean equals(Object value) { return this == value || "raw".equals(value); }
            }));
        }
    }

    public static class Outer implements IClientLayerFactory {
        public int order() { return 200; }
        public <P> Optional<InvocationHandler> wrap(Class<P> type, P inner, IClientConfig config,
                IClientModifier... modifiers) {
            if (type != Client.class) {
                return Optional.empty();
            }
            return Optional.of((proxy, method, args) -> {
                TRACE.get().add(order());
                if (method.getDeclaringClass() == Object.class && method.getName().equals("equals") && args[0] == proxy) {
                    args = new Object[]{inner};
                }
                try {
                    return method.invoke(inner, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        }
    }
    public static class Inner extends Outer {
        public int order() { return 300; }
    }

    @Test
    void fallbackNamesUnsupportedMethod() throws Exception {
        var client = IClient.create(Missing.class, null);
        assertTrue(client.equals(client));
        var failure = assertThrows(UnsupportedOperationException.class, client::missing);
        assertEquals("No IClientVendor for " + Missing.class.getName() + ".missing", failure.getMessage());
        assertTrue(IClientContext.current().isEmpty());
    }

    @Test
    void wrapsInOrderAndPreservesCheckedExceptionIdentity() throws Exception {
        var client = IClient.create(Client.class, null);
        TRACE.get().clear();
        assertSame(FAILURE, assertThrows(IOException.class, client::fail));
        assertEquals(List.of(200, 300), TRACE.get());
        assertTrue(IClientContext.current().isEmpty());
        assertSame(FAILURE, assertThrows(IOException.class, client::mapped));
        assertEquals(99, client.hashCode());
        assertEquals("raw", client.toString());
        assertTrue(client.equals("raw"));
        assertTrue(client.equals(client));
    }

    @Test
    void restoresNestedContext() throws Exception {
        var client = IClient.create(Client.class, null);
        assertEquals("inner", client.outer(IClient.create(Client.class, null)));
        assertTrue(IClientContext.current().isEmpty());
    }
}
