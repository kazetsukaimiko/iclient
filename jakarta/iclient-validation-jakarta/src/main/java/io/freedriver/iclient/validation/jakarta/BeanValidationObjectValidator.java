package io.freedriver.iclient.validation.jakarta;

import io.freedriver.iclient.validation.api.ObjectValidator;
import io.freedriver.iclient.validation.api.Violation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Objects;

/** Adapts Bean Validation property paths and constraint metadata to the client API. */
public final class BeanValidationObjectValidator implements ObjectValidator {
    private final Validator validator;

    public BeanValidationObjectValidator() {
        this(DefaultValidator.INSTANCE);
    }

    public BeanValidationObjectValidator(Validator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    @Override
    public List<Violation> validate(Object object) {
        if (object == null) {
            return List.of();
        }
        return validator.validate(object).stream().map(v -> new Violation(v.getPropertyPath().toString(),
                v.getMessage(), v.getConstraintDescriptor().getAnnotation().annotationType().getName())).toList();
    }
}
