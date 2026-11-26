package io.freedriver.iclient.http.impl.microprofile;

import io.freedriver.iclient.api.IClientConfig;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.api.IClientVendor;
import io.freedriver.iclient.api.exception.IClientConstructionException;
import io.freedriver.iclient.http.api.config.HttpClientConfig;
import io.freedriver.iclient.http.api.modifiers.ComposingFilter;
import io.freedriver.iclient.http.api.modifiers.IClientHttpRequestFilter;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/** Builds an HTTP client with MicroProfile {@code RestClientBuilder}. */
public final class MicroProfileHttpVendor implements IClientVendor {
    static final String CONSTRUCTION_FAILED = "HTTP client construction failed";

    @Override
    public <P> Optional<P> createClient(Class<P> apiClass, IClientConfig config, IClientModifier... modifiers)
            throws IClientConstructionException {
        if (!(config instanceof HttpClientConfig http) || !apiClass.isAnnotationPresent(Path.class)) {
            return Optional.empty();
        }
        var filters = ComposingFilter.of(Stream.of(modifiers)
                .filter(IClientHttpRequestFilter.class::isInstance)
                .map(IClientHttpRequestFilter.class::cast));
        try {
            P delegate = RestClientBuilder.newBuilder()
                    .baseUri(directoryBase(http.baseUri()))
                    .followRedirects(false)
                    .register(new RequestFilterAdapter(filters))
                    .register(new HttpStatusMapper(), Priorities.USER)
                    .build(apiClass);
            return Optional.of(MicroProfileClient.proxy(apiClass, delegate));
        } catch (RuntimeException failure) {
            throw new IClientConstructionException(CONSTRUCTION_FAILED, failure);
        }
    }

    /** A base path without a trailing slash drops its last segment when a relative path is applied. */
    static URI directoryBase(URI base) throws IClientConstructionException {
        var path = base.getRawPath();
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return base;
        }
        try {
            return new URI(base.getScheme(), base.getRawUserInfo(), base.getHost(), base.getPort(),
                    path + "/", base.getRawQuery(), base.getRawFragment());
        } catch (URISyntaxException failure) {
            throw new IClientConstructionException(CONSTRUCTION_FAILED, failure);
        }
    }
}
