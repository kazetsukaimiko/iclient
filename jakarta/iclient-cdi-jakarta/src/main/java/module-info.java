module io.freedriver.iclient.cdi.jakarta {
    requires io.freedriver.iclient.api;
    requires jakarta.cdi;
    provides io.freedriver.iclient.api.ext.BeanCreator with io.freedriver.iclient.cdi.jakarta.CdiBeanCreator;
}
