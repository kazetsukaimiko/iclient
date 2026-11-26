package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.api.IClientContext;
import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.http.api.exception.IClientHttpCallException;
import jakarta.ws.rs.ProcessingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** The client {@code createClient} returns. It translates vendor failures on the caller's thread. */
final class MicroProfileClient {
    static final String CALL_FAILED = "HTTP call failed";

    private MicroProfileClient() {}

    static <P> P proxy(Class<P> apiClass, P delegate) {
        return apiClass.cast(Proxy.newProxyInstance(apiClass.getClassLoader(), new Class<?>[] {apiClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> apiClass.getName();
                            default -> null;
                        };
                    }
                    if (IClientContext.current().isEmpty()) {
                        throw new IClientCallException(RequestFilterAdapter.NO_CONTEXT, null);
                    }
                    var state = CallState.open();
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                        throw translate(state, failure.getCause());
                    } finally {
                        state.close();
                    }
                }));
    }

    private static RuntimeException translate(CallState state, Throwable failure) {
        if (state.filterFailure != null) {
            return new IClientCallException(RequestFilterAdapter.FILTER_FAILED, state.filterFailure);
        }
        if (state.bodyRejected != null) {
            return new IClientCallException(RequestFilterAdapter.BODY_REPLACED, state.bodyRejected);
        }
        var current = unwrap(failure);
        if (current instanceof IClientHttpCallException http) {
            return http;
        }
        if (current instanceof HttpStatusMapper.UnattachedHttpFailure unattached) {
            if (state.sent == null) {
                return new IClientCallException(CALL_FAILED, unattached);
            }
            return new IClientHttpCallException(state.sent, unattached.response);
        }
        if (current instanceof IClientCallException call) {
            return call;
        }
        return new IClientCallException(CALL_FAILED, failure);
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while (current instanceof ProcessingException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
