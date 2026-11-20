module io.freedriver.iclient.api {
    requires static org.jspecify;
    exports io.freedriver.iclient.api;
    exports io.freedriver.iclient.api.annotation;
    exports io.freedriver.iclient.api.exception;
    exports io.freedriver.iclient.api.ext;
    uses io.freedriver.iclient.api.IClientHandlerFactory;
    uses io.freedriver.iclient.api.IClientCallDelegator;
    uses io.freedriver.iclient.api.ext.BeanCreator;
    provides io.freedriver.iclient.api.ext.BeanCreator with io.freedriver.iclient.api.ext.NoArgBeanCreator;
}
