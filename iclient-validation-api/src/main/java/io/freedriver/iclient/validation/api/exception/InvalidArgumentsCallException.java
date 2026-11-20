package io.freedriver.iclient.validation.api.exception;

import io.freedriver.iclient.validation.api.Violation;
import java.util.List;

public class InvalidArgumentsCallException extends IClientValidationException {
    public InvalidArgumentsCallException(List<Violation> violations) {
        super(describe(violations), violations);
    }
}
