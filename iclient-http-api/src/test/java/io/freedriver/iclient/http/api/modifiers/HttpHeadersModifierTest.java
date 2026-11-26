package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpHeadersModifierTest {
    private static final String SECRET = "Bearer dummy-secret";

    @Test
    void upgradesIntoOneFilterAndNotItself() {
        var modifier = new HttpHeadersModifier(Map.of("Authorization", SECRET));
        var expanded = modifier.upgrade().toList();
        assertEquals(1, expanded.size());
        assertInstanceOf(IClientHttpRequestFilter.class, expanded.get(0));
        assertFalse(expanded.get(0) instanceof HttpHeadersModifier);
    }

    @Test
    void upgradedFilterReplacesHeadersWhateverTheirCase() throws IOException {
        var request = new MutableHttpRequest();
        request.headers().put("authorization", new ArrayList<>(List.of("old-one", "old-two")));
        request.headers().put("Accept", new ArrayList<>(List.of("text/plain")));
        filter(new HttpHeadersModifier(Map.of("AUTHORIZATION", SECRET, "X-List", "a, b"))).filter(request, null);
        assertEquals(List.of(SECRET), request.headers().get("Authorization"));
        assertEquals(List.of("a, b"), request.headers().get("x-list"));
        assertEquals(List.of("text/plain"), request.headers().get("accept"));
        assertEquals(3, request.headers().size());
    }

    @Test
    void copiesItsHeadersAndRejectsNamesThatDifferOnlyInCase() {
        var source = new HashMap<>(Map.of("Authorization", SECRET));
        var modifier = new HttpHeadersModifier(source);
        source.clear();
        assertEquals(SECRET, modifier.headers().get("authorization"));
        assertThrows(UnsupportedOperationException.class, () -> modifier.headers().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new HttpHeadersModifier(Map.of("Authorization", "a", "authorization", "b")));
    }

    @Test
    void formattingTheModifierAndItsFilterOmitsHeaderValues() {
        var modifier = new HttpHeadersModifier(Map.of("Authorization", SECRET, "X-Api-Key", "dummy-secret"));
        assertEquals("HttpHeadersModifier[names=[Authorization, X-Api-Key]]", modifier.toString());
        assertEquals("HttpHeadersFilter[names=[Authorization, X-Api-Key]]", filter(modifier).toString());
    }

    private static IClientHttpRequestFilter filter(IClientModifier modifier) {
        return (IClientHttpRequestFilter) modifier.upgrade().findFirst().orElseThrow();
    }
}
