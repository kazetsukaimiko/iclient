package io.freedriver.iclient.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A call and its immutable argument list, which preserves null arguments. */
public record IClientCall(Class<?> interfaceClass, Object proxy, Method method, List<Object> args) {
    public IClientCall {
        args = args == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(args));
    }

    public static IClientCall of(Class<?> interfaceClass, Object proxy, Method method, Object[] args) {
        return new IClientCall(interfaceClass, proxy, method, args == null ? List.of() : Arrays.asList(args));
    }
}
