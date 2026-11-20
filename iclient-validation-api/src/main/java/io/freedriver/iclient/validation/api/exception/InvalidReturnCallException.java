package io.freedriver.iclient.validation.api.exception;

import io.freedriver.iclient.validation.api.Violation;
import java.util.List;

public class InvalidReturnCallException extends IClientValidationException {
    private final Object returnValue;

    public InvalidReturnCallException(List<Violation> violations, Object returnValue) {
        super(describe(violations), violations);
        this.returnValue = returnValue;
    }

    public Object returnValue() {
        return returnValue;
    }

    public <T> T returnValue(Class<T> type) {
        return type.cast(returnValue);
    }
}
