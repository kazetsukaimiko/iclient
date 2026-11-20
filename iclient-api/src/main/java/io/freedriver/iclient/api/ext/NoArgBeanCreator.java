package io.freedriver.iclient.api.ext;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Optional;

/** Constructs concrete classes through their public no-argument constructor. */
public final class NoArgBeanCreator implements BeanCreator {
    @Override
    public <T> Optional<T> create(Class<T> type) throws IClientConstructionException {
        if (Modifier.isAbstract(type.getModifiers()) || type.isInterface()) {
            return Optional.empty();
        }
        try {
            return Optional.of(type.getConstructor().newInstance());
        } catch (NoSuchMethodException missing) {
            return Optional.empty();
        } catch (InvocationTargetException failure) {
            throw new IClientConstructionException("Cannot construct " + type.getName(), failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IClientConstructionException("Cannot construct " + type.getName(), failure);
        }
    }
}
