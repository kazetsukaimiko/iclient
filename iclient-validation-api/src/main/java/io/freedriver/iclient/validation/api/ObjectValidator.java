package io.freedriver.iclient.validation.api;

import java.util.List;

/** Validates an object, returning violations with paths relative to the object. */
public interface ObjectValidator {
    List<Violation> validate(Object object);
}
