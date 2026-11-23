package io.freedriver.iclient.http.api.config;

import io.freedriver.iclient.api.IClientConfig;
import java.net.URI;
import java.time.Duration;

/**
 * Where an HTTP client sends its calls and how long it waits.
 *
 * <p>{@code baseUri} is an http or https URI with a host. Both timeouts are positive.
 * {@link #toString()} shows the base URI's scheme, host, port and path, and omits its userinfo,
 * query and fragment.
 */
public record HttpClientConfig(URI baseUri, Duration connectTimeout, Duration readTimeout) implements IClientConfig {
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
        requirePositive("Connect timeout", connectTimeout);
        requirePositive("Read timeout", readTimeout);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
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
        return "HttpClientConfig[baseUri=" + display + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
