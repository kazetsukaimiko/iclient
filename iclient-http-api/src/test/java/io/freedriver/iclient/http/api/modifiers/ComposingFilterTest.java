package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComposingFilterTest {
    private final List<String> runs = new ArrayList<>();

    private IClientHttpRequestFilter named(String name) {
        return (request, call) -> runs.add(name);
    }

    @Test
    void addReturnsANewFilterAndLeavesTheOriginalUnchanged() throws IOException {
        var empty = new ComposingFilter();
        var one = empty.add(named("a"));
        var two = one.add(named("b"));
        assertNotSame(empty, one);
        empty.filter(new MutableHttpRequest(), null);
        assertEquals(List.of(), runs);
        one.filter(new MutableHttpRequest(), null);
        assertEquals(List.of("a"), runs);
        two.filter(new MutableHttpRequest(), null);
        assertEquals(List.of("a", "a", "b"), runs);
        assertThrows(IllegalArgumentException.class, () -> empty.add(null));
        assertThrows(IllegalArgumentException.class, () -> ComposingFilter.of(Stream.of(named("a"), null)));
    }

    @Test
    void composesTheFiltersAmongModifiersInOrder() throws IOException {
        IClientModifier[] modifiers = {named("a"), new IClientModifier() {}, named("b"), named("c")};
        var reduced = Stream.of(modifiers)
                .filter(IClientHttpRequestFilter.class::isInstance).map(IClientHttpRequestFilter.class::cast)
                .reduce(new ComposingFilter(), ComposingFilter::add, ComposingFilter::add);
        reduced.filter(new MutableHttpRequest(), null);
        assertEquals(List.of("a", "b", "c"), runs);
        runs.clear();
        ComposingFilter.of(Stream.of(named("a"), named("b"), named("c"))).filter(new MutableHttpRequest(), null);
        assertEquals(List.of("a", "b", "c"), runs);
    }

    @Test
    void laterFiltersSeeEarlierChangesAndAnIoExceptionStopsTheRest() {
        var error = new IOException("filter failed");
        var request = new MutableHttpRequest();
        var composed = new ComposingFilter()
                .add((r, call) -> r.setMethod("PUT"))
                .add((r, call) -> runs.add(r.method()))
                .add((r, call) -> { throw error; })
                .add(named("after"));
        assertSame(error, assertThrows(IOException.class, () -> composed.filter(request, null)));
        assertEquals(List.of("PUT"), runs);
    }

    @Test
    void formattingOmitsHeaderValues() {
        var headers = (IClientHttpRequestFilter) new HttpHeadersModifier(Map.of("Authorization", "Bearer dummy-secret"))
                .upgrade().findFirst().orElseThrow();
        assertEquals("ComposingFilter[HttpHeadersFilter[names=[Authorization]]]",
                new ComposingFilter().add(headers).toString());
    }
}
