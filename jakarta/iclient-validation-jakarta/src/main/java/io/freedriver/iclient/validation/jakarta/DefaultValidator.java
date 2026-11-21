package io.freedriver.iclient.validation.jakarta;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Initialized only when a provider needs the default Bean Validation configuration. */
final class DefaultValidator {
    static final Validator INSTANCE = Validation.buildDefaultValidatorFactory().getValidator();

    private DefaultValidator() {}
}
