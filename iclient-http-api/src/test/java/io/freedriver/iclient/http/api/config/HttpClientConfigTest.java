package io.freedriver.iclient.http.api.config;

import io.freedriver.iclient.api.IClientConfig;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientConfigTest {
    private static final URI BASE = URI.create("https://example.org:8443/api");

    @Test
    void builderCarriesTheBaseUri() {
        IClientConfig config = HttpClientConfig.builder().baseUri(BASE).build();
        assertEquals(BASE, assertInstanceOf(HttpClientConfig.class, config).baseUri());
        assertDoesNotThrow(() -> config(URI.create("HTTP://localhost")));
    }

    @Test
    void rejectsBaseUrisThatAreNotHttpWithAHost() {
        for (var uri : new String[]{"/api", "//example.org/api", "ftp://example.org/api", "http:relative",
                "https:///api", "mailto:someone@example.org"}) {
            assertThrows(IllegalArgumentException.class, () -> config(URI.create(uri)), uri);
        }
        assertThrows(IllegalArgumentException.class, () -> HttpClientConfig.builder().build());
    }

    @Test
    void formattingOmitsUserInfoQueryAndFragment() {
        var uri = URI.create("https://user:dummy-secret@example.org:8443/api?token=dummy-secret#frag");
        var builder = HttpClientConfig.builder().baseUri(uri);
        assertEquals("HttpClientConfig[baseUri=https://example.org:8443/api]", builder.build().toString());
        assertFalse(builder.toString().contains("dummy-secret"), builder.toString());
        assertEquals(uri, builder.build().baseUri());
    }

    private static HttpClientConfig config(URI uri) {
        return HttpClientConfig.builder().baseUri(uri).build();
    }
}
