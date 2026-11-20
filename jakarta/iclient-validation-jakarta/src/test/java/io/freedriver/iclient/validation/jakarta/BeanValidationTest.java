package io.freedriver.iclient.validation.jakarta;

import io.freedriver.iclient.validation.api.MethodCallValidator;
import io.freedriver.iclient.validation.api.ObjectValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeanValidationTest {
    static class Address {
        @NotNull String zip;
    }

    static class Request {
        @Valid Address address = new Address();
    }

    interface Client {
        @Valid Request checked(@Valid Request checked, Request unchecked);
        Request plain();
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
