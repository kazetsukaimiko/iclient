package io.freedriver.iclient.validation.jakarta;

import io.freedriver.iclient.api.IClient;
import io.freedriver.iclient.api.IClientConfig;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.api.IClientVendor;
import io.freedriver.iclient.validation.api.exception.InvalidArgumentsCallException;
import io.freedriver.iclient.validation.api.exception.InvalidReturnCallException;
import io.freedriver.iclient.validation.api.MethodCallValidator;
import io.freedriver.iclient.validation.api.ObjectValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BeanValidationTest {
    static class Address {
        @NotNull String zip;
    }

    public static class Request {
        @Valid Address address = new Address();
    }

    public interface Client {
        @Valid Request checked(@Valid Request checked, Request unchecked);
        Request plain();
    }

    public static class Vendor implements IClientVendor {
        public <P> Optional<P> createClient(Class<P> type, IClientConfig config, IClientModifier... modifiers) {
            if (type != Client.class) {
                return Optional.empty();
            }
            return Optional.of(type.cast(new Client() {
                public Request checked(Request checked, Request unchecked) { return unchecked; }
                public Request plain() { return new Request(); }
            }));
        }
    }

    @Test
    void clientInstallsTheRealValidationLayer() throws Exception {
        var client = IClient.create(Client.class, null);
        assertThrows(InvalidArgumentsCallException.class, () -> client.checked(new Request(), null));
        var invalid = new Request();
        var failure = assertThrows(InvalidReturnCallException.class, () -> client.checked(null, invalid));
        assertSame(invalid, failure.returnValue());
        assertEquals("return.address.zip", failure.violations().get(0).path());
        assertNotNull(client.plain());
    }

    @Test
    void validatesOnlyMarkedNonNullParametersAndPrefixesTheirNames() throws ReflectiveOperationException {
        var calls = ServiceLoader.load(MethodCallValidator.class).findFirst().orElseThrow();
        assertInstanceOf(BeanValidationMethodCallValidator.class, calls);
        var method = Client.class.getMethod("checked", Request.class, Request.class);
        var failures = calls.validate(method, new Object[]{new Request(), new Request()});
        assertEquals(1, failures.size());
        assertEquals(method.getParameters()[0].getName() + ".address.zip", failures.get(0).path());
        assertEquals(NotNull.class.getName(), failures.get(0).constraint());
        assertTrue(calls.validate(method, new Object[]{null, new Request()}).isEmpty());
        assertTrue(calls.validatesArguments(method));
        assertFalse(calls.validatesArguments(Client.class.getMethod("plain")));
        assertTrue(calls.validatesReturnValue(method));
        assertFalse(calls.validatesReturnValue(Client.class.getMethod("plain")));
    }

    @Test
    void objectValidatorCascadesFromRoot() {
        var objects = ServiceLoader.load(ObjectValidator.class).findFirst().orElseThrow();
        assertInstanceOf(BeanValidationObjectValidator.class, objects);
        var failures = objects.validate(new Request());
        assertEquals(1, failures.size());
        assertEquals("address.zip", failures.get(0).path());
    }
}
