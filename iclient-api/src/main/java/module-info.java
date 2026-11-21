module io.freedriver.iclient.api {
    requires static org.jspecify;
    exports io.freedriver.iclient.api;
    exports io.freedriver.iclient.api.annotation;
    exports io.freedriver.iclient.api.exception;
    exports io.freedriver.iclient.api.ext;
    uses io.freedriver.iclient.api.IClientHandlerFactory;
    uses io.freedriver.iclient.api.IClientVendor;
    uses io.freedriver.iclient.api.IClientLayerFactory;
    provides io.freedriver.iclient.api.IClientLayerFactory with
            io.freedriver.iclient.api.ContextLayerFactory,
            io.freedriver.iclient.api.ExceptionMappingLayerFactory;
    uses io.freedriver.iclient.api.ext.BeanCreator;
    provides io.freedriver.iclient.api.ext.BeanCreator with io.freedriver.iclient.api.ext.NoArgBeanCreator;
}
