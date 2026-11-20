module io.freedriver.iclient.validation.api {
    requires transitive io.freedriver.iclient.api;
    exports io.freedriver.iclient.validation.api;
    exports io.freedriver.iclient.validation.api.exception;
    uses io.freedriver.iclient.validation.api.MethodCallValidator;
    uses io.freedriver.iclient.validation.api.ObjectValidator;
    provides io.freedriver.iclient.api.IClientCallDelegator with io.freedriver.iclient.validation.api.ValidatingCallDelegator;
}
