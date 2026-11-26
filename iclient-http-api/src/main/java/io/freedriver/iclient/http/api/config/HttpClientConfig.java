package io.freedriver.iclient.http.api.config;

import io.freedriver.iclient.api.IClientConfig;
import java.net.URI;
import lombok.Builder;

/**
 * Where an HTTP client sends its calls. Build one with {@link #builder()}.
 *
 * <p>{@code baseUri} is an http or https URI with a host. Timeouts, connection pools, TLS, resiliency
 * and metrics are modifiers, not config. {@link #toString()} shows the base URI's scheme, host, port
 * and path, and omits its userinfo, query and fragment; the builder's omits the URI.
 */
@Builder
public record HttpClientConfig(URI baseUri) implements IClientConfig {
    public HttpClientConfig {
        if (baseUri == null) {
            throw new IllegalArgumentException("Base URI is required");
        }
        var scheme = baseUri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Base URI scheme must be http or https: " + scheme);
        }
        if (baseUri.getHost() == null) {
            throw new IllegalArgumentException("Base URI must have a host");
        }
    }

    @Override
    public String toString() {
        var display = new StringBuilder(baseUri.getScheme()).append("://").append(baseUri.getHost());
        if (baseUri.getPort() >= 0) {
            display.append(':').append(baseUri.getPort());
        }
        if (baseUri.getRawPath() != null) {
            display.append(baseUri.getRawPath());
        }
        return "HttpClientConfig[baseUri=" + display + "]";
    }

    /** Collects the config's values. Its {@link #toString()} omits them. */
    public static class HttpClientConfigBuilder {
        @Override
        public String toString() {
            return "HttpClientConfig.HttpClientConfigBuilder";
        }
    }
}
