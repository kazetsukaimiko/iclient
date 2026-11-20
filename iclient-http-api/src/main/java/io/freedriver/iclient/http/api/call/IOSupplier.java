package io.freedriver.iclient.http.api.call;

import java.io.IOException;

/** Supplies a value from an operation that can fail with an I/O error. */
@FunctionalInterface
public interface IOSupplier<T> {
    T get() throws IOException;
}
