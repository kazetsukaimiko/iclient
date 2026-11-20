package io.freedriver.iclient.validation.jakarta;

import io.freedriver.iclient.validation.api.MethodCallValidator;
import io.freedriver.iclient.validation.api.Violation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Validates cascaded parameters using the interface's declared parameter names when available. */
public final class BeanValidationMethodCallValidator implements MethodCallValidator {
    private final BeanValidationObjectValidator objects;

    public BeanValidationMethodCallValidator() {
        this(DefaultValidator.INSTANCE);
    }

    public BeanValidationMethodCallValidator(Validator validator) {
        objects = new BeanValidationObjectValidator(validator);
    }

    @Override
    public List<Violation> validate(Method method, Object[] args) {
        var failures = new ArrayList<Violation>();
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
            if (parameter.isAnnotationPresent(Valid.class) && args[i] != null) {
                for (var violation : objects.validate(args[i])) {
                    String name = parameter.getName();
                    String path = violation.path().isEmpty() ? name : name + "." + violation.path();
                    failures.add(new Violation(path, violation.message(), violation.constraint()));
                }
            }
        }
        return List.copyOf(failures);
    }

    @Override
    public boolean validatesArguments(Method method) {
        return Arrays.stream(method.getParameters()).anyMatch(parameter -> parameter.isAnnotationPresent(Valid.class));
    }

    @Override
    public boolean validatesReturnValue(Method method) {
        return method.isAnnotationPresent(Valid.class);
    }
}
