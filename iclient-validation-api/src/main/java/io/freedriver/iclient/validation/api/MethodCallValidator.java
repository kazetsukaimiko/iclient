package io.freedriver.iclient.validation.api;

import java.lang.reflect.Method;
import java.util.List;

/** Selects marked arguments and reports violations relative to their parameter names. */
public interface MethodCallValidator {
    List<Violation> validate(Method method, Object[] args);
    boolean validatesReturnValue(Method method);
}
