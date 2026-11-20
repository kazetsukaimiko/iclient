package io.freedriver.iclient.validation.api.exception;

import io.freedriver.iclient.api.exception.IClientCallException;
import io.freedriver.iclient.validation.api.Violation;
import java.util.List;
import java.util.stream.Collectors;

/** Unchecked call failure carrying the violations without logging constraint messages or values. */
public abstract class IClientValidationException extends IClientCallException {
    private final List<Violation> violations;

    protected IClientValidationException(String message, List<Violation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
        if (this.violations.isEmpty()) {
            throw new IllegalArgumentException("A validation failure requires violations");
        }
    }

    static String describe(List<Violation> violations) {
        return violations.size() + " violation(s): " + violations.stream()
                .map(Violation::path).collect(Collectors.joining(", "));
    }

    public List<Violation> violations() {
        return violations;
    }
}
