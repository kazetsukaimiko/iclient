package io.freedriver.iclient.cdi.jakarta;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.api.ext.BeanCreator;
import jakarta.enterprise.inject.spi.CDI;
import java.util.Optional;

/**
 * Resolves the default-qualified bean before the no-argument creator is tried.
 * Prefer {@code @ApplicationScoped} or {@code @Singleton} mappers. A {@code @Dependent} instance lives as long
 * as the client proxy holding it, and iclient never destroys it.
 * In Quarkus, mark beans used only through this lookup with {@code @Unremovable}
 * or list them in {@code quarkus.arc.unremovable-types}.
 */
public final class CdiBeanCreator implements BeanCreator {
    @Override
    public <T> Optional<T> create(Class<T> type) throws IClientConstructionException {
        CDI<Object> container;
        try {
            container = CDI.current();
        } catch (IllegalStateException noContainer) {
            return Optional.empty();
        }
        var instance = container.select(type);
        if (instance.isUnsatisfied()) {
            return Optional.empty();
        }
        if (instance.isAmbiguous()) {
            throw new IClientConstructionException("More than one CDI bean for " + type.getName());
        }
        return Optional.of(instance.get());
    }
}
