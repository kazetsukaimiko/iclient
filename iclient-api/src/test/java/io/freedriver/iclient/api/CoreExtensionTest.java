package io.freedriver.iclient.api;

import io.freedriver.iclient.api.annotation.MapExceptions;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.api.exception.IClientExceptionMapper;
import io.freedriver.iclient.api.ext.NoArgBeanCreator;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CoreExtensionTest {
    private final NoArgBeanCreator creator = new NoArgBeanCreator();

    public static class Plain {}
    public abstract static class Abstract {}
    public static class NeedsArgument {
        public NeedsArgument(String arg) {}
    }
    public static class Broken {
        public Broken() {
            throw new IllegalStateException("broken");
        }
    }

    public static class Mapper implements IClientExceptionMapper {
        public Optional<Throwable> map(IClientCallException failure) {
            return Optional.empty();
        }
    }

    @MapExceptions(using = Mapper.class)
    interface Service {
        @MapExceptions(using = Mapper.class)
        void call();
    }

    @Test
    void createsPublicNoArgClass() throws IClientConstructionException {
        assertInstanceOf(Plain.class, creator.create(Plain.class).orElseThrow());
    }

    @Test
    void declinesTypesWithoutUsableConstructor() throws IClientConstructionException {
        assertTrue(creator.create(Service.class).isEmpty());
        assertTrue(creator.create(Abstract.class).isEmpty());
        assertTrue(creator.create(NeedsArgument.class).isEmpty());
    }

    @Test
    void preservesConstructorFailure() {
        var failure = assertThrows(IClientConstructionException.class, () -> creator.create(Broken.class));
        assertTrue(failure.getMessage().contains(Broken.class.getName()));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void mapperSelectionIsVisibleOnInterfaceAndMethod() throws ReflectiveOperationException {
        assertEquals(Mapper.class, Service.class.getAnnotation(MapExceptions.class).using());
        assertEquals(Mapper.class, Service.class.getMethod("call").getAnnotation(MapExceptions.class).using());
    }
}
