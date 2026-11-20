module io.freedriver.iclient.cdi.jakarta {
    requires io.freedriver.iclient.api;
    requires static jakarta.cdi;
    provides io.freedriver.iclient.api.ext.BeanCreator with io.freedriver.iclient.cdi.jakarta.CdiBeanCreator;
}
