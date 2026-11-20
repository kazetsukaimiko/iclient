package io.freedriver.iclient.api.exception;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

/**
 * Maps the handler or delegator's failure once per failed call. An empty result preserves
 * the original exception. A runtime exception, error, or checked exception declared by the
 * invoked method is thrown directly. For an undeclared checked result or a mapper failure,
 * the proxy throws the original with the secondary throwable suppressed.
 */
public interface IClientExceptionMapper {
    Optional<Throwable> map(@NonNull IClientCallException callException);
}
