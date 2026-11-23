package io.freedriver.iclient.http.api.config;

import io.freedriver.iclient.api.IClientConfig;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientConfigTest {
    private static final URI BASE = URI.create("https://example.org:8443/api");
    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration READ = Duration.ofSeconds(30);

    @Test
    void carriesBaseUriAndTimeouts() {
        IClientConfig config = new HttpClientConfig(BASE, CONNECT, READ);
        var http = assertInstanceOf(HttpClientConfig.class, config);
        assertEquals(BASE, http.baseUri());
        assertEquals(CONNECT, http.connectTimeout());
        assertEquals(READ, http.readTimeout());
        assertDoesNotThrow(() -> new HttpClientConfig(URI.create("HTTP://localhost"), CONNECT, READ));
    }

    @Test
    void rejectsBaseUrisThatAreNotHttpWithAHost() {
        for (var uri : new String[]{"/api", "//example.org/api", "ftp://example.org/api", "http:relative",
                "https:///api", "mailto:someone@example.org"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HttpClientConfig(URI.create(uri), CONNECT, READ), uri);
        }
        assertThrows(IllegalArgumentException.class, () -> new HttpClientConfig(null, CONNECT, READ));
    }

    @Test
    void rejectsTimeoutsThatAreNotPositive() {
        for (var timeout : new Duration[]{null, Duration.ZERO, Duration.ofMillis(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> new HttpClientConfig(BASE, timeout, READ));
            assertThrows(IllegalArgumentException.class, () -> new HttpClientConfig(BASE, CONNECT, timeout));
        }
    }

    @Test
    void formattingOmitsUserInfoQueryAndFragment() {
        var uri = URI.create("https://user:dummy-secret@example.org:8443/api?token=dummy-secret#frag");
        var config = new HttpClientConfig(uri, CONNECT, READ);
        assertEquals("HttpClientConfig[baseUri=https://example.org:8443/api, connectTimeout=PT5S, readTimeout=PT30S]",
                config.toString());
        assertEquals(uri, config.baseUri());
    }
}
