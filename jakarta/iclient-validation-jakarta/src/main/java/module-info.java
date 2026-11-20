module io.freedriver.iclient.validation.jakarta {
    requires io.freedriver.iclient.validation.api;
    requires static jakarta.validation;
    exports io.freedriver.iclient.validation.jakarta;
    provides io.freedriver.iclient.validation.api.ObjectValidator with io.freedriver.iclient.validation.jakarta.BeanValidationObjectValidator;
    provides io.freedriver.iclient.validation.api.MethodCallValidator with io.freedriver.iclient.validation.jakarta.BeanValidationMethodCallValidator;
}
