package io.freedriver.iclient.validation.api;

import io.freedriver.iclient.api.IClientCall;
import io.freedriver.iclient.validation.api.exception.InvalidArgumentsCallException;
import io.freedriver.iclient.validation.api.exception.InvalidReturnCallException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidatingCallDelegatorTest {
    private record Calls(List<Violation> failures, boolean marked) implements MethodCallValidator {
        public List<Violation> validate(Method method, Object[] args) {
            return failures;
        }

        public boolean validatesReturnValue(Method method) {
            return marked;
        }
    }

    private static final IClientCall CALL = IClientCall.of(Runnable.class, null, null, null);
    private static final Violation BAD = new Violation("field", "secret input", "Constraint");

    @Test
    void contextCopiesArgumentsIncludingNullElements() {
        Object[] args = {"before", null};
        var call = IClientCall.of(Runnable.class, this, null, args);
        args[0] = "after";
        assertEquals("before", call.args().get(0));
        assertNull(call.args().get(1));
        assertThrows(UnsupportedOperationException.class, () -> call.args().clear());
        assertTrue(CALL.args().isEmpty());
    }

    @Test
    void invalidArgumentsStopTheCallAndOnlyPathsReachTheMessage() {
        var delegator = new ValidatingCallDelegator(new Calls(List.of(BAD), false), result -> fail());
        var failure = assertThrows(InvalidArgumentsCallException.class,
                () -> delegator.invoke(CALL, () -> fail("delegate called")));
        assertEquals("1 violation(s): field", failure.getMessage());
        assertEquals(List.of(BAD), failure.violations());
        assertThrows(UnsupportedOperationException.class, () -> failure.violations().clear());
    }

    @Test
    void invalidMarkedReturnPreservesTheResult() {
        var delegator = new ValidatingCallDelegator(new Calls(List.of(), true), value -> List.of(BAD));
        var result = new StringBuilder("secret");
        var failure = assertThrows(InvalidReturnCallException.class, () -> delegator.invoke(CALL, () -> result));
        assertSame(result, failure.returnValue());
        assertSame(result, failure.returnValue(StringBuilder.class));
        assertEquals("1 violation(s): return.field", failure.getMessage());
    }

    @Test
    void unmarkedAndNullResultsBypassObjectValidation() {
        ObjectValidator unused = result -> fail("validated result");
        var unmarked = new ValidatingCallDelegator(new Calls(List.of(), false), unused);
        assertSame(this, unmarked.invoke(CALL, () -> this));
        var marked = new ValidatingCallDelegator(new Calls(List.of(), true), unused);
        assertNull(marked.invoke(CALL, () -> null));
    }

    @Test
    void noProvidersPassesThrough() {
        assertSame(this, new ValidatingCallDelegator().invoke(CALL, () -> this));
    }
}
