module io.freedriver.iclient.http.microprofile {
    requires io.freedriver.iclient.http.api;
    requires jakarta.ws.rs;
    requires microprofile.rest.client.api;

    provides io.freedriver.iclient.api.IClientVendor
            with io.freedriver.iclient.http.impl.microprofile.MicroProfileHttpVendor;
}
