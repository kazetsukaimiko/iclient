package io.freedriver.iclient.api.ext;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import java.util.Optional;

/**
 * Creates annotation-named classes. Empty means this provider declines the class.
 * Proxy factories try providers in ServiceLoader order with NoArgBeanCreator last.
 */
public interface BeanCreator {
    <T> Optional<T> create(Class<T> type) throws IClientConstructionException;
}
