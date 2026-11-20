package io.freedriver.iclient.cdi.jakarta;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.api.ext.BeanCreator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ServiceLoader;
import org.jboss.weld.environment.se.Weld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CdiBeanCreatorTest {
    interface Choice {}
    @ApplicationScoped
    public static class First implements Choice {}
    @ApplicationScoped
    public static class Second implements Choice {}
    @ApplicationScoped
    public static class Mapper {
        @Inject First dependency;
        public boolean injected() {
            return dependency != null;
        }
    }
    static class Unregistered {}

    private final CdiBeanCreator creator = new CdiBeanCreator();

    @Test
    void declinesWhenNoContainerIsRunning() throws IClientConstructionException {
        assertTrue(creator.create(Mapper.class).isEmpty());
    }

    @Test
    void resolvesInjectedBeanAndDistinguishesMissingAndAmbiguousBeans() throws IClientConstructionException {
        try (var container = new Weld().disableDiscovery()
                .addBeanClasses(Mapper.class, First.class, Second.class).initialize()) {
            assertTrue(creator.create(Mapper.class).orElseThrow().injected());
            assertTrue(creator.create(Unregistered.class).isEmpty());
            var failure = assertThrows(IClientConstructionException.class, () -> creator.create(Choice.class));
            assertTrue(failure.getMessage().contains(Choice.class.getName()));
        }
    }

    @Test
    void serviceLoaderDiscoversProvider() {
        assertTrue(ServiceLoader.load(BeanCreator.class).stream()
                .anyMatch(provider -> provider.type() == CdiBeanCreator.class));
    }
}
