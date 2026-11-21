package io.freedriver.iclient.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** The synchronous call in progress on this thread. Nested calls restore the enclosing context. */
public record IClientContext(Class<?> apiClass, Method method, List<Object> args,
        IClientConfig config, List<IClientModifier> modifiers) {
    static final ThreadLocal<IClientContext> ACTIVE = new ThreadLocal<>();

    public IClientContext {
        args = args == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(args));
        modifiers = List.copyOf(modifiers);
    }

    public static Optional<IClientContext> current() {
        return Optional.ofNullable(ACTIVE.get());
    }
}
