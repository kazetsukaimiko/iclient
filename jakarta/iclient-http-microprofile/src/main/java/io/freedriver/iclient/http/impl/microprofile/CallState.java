package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.http.api.call.ImmutableHttpRequest;
import java.io.IOException;

/** Per-call state for the thread that invoked the client. One call does not see another's. */
final class CallState {
    private static final ThreadLocal<CallState> ACTIVE = new ThreadLocal<>();

    ImmutableHttpRequest sent;
    IOException filterFailure;
    RuntimeException bodyRejected;

    static CallState open() {
        var state = new CallState();
        ACTIVE.set(state);
        return state;
    }

    static CallState current() {
        return ACTIVE.get();
    }

    void close() {
        ACTIVE.remove();
    }
}
