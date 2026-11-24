package io.freedriver.iclient.http.api.modifiers;

import io.freedriver.iclient.api.IClientContext;
import io.freedriver.iclient.http.api.call.MutableHttpRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs filters in the order they were added. Immutable: {@link #add} returns a new instance.
 *
 * <p>A vendor composes the filters among its modifiers with {@link #of}, or with the three-argument
 * reduce: {@code filters.reduce(new ComposingFilter(), ComposingFilter::add, ComposingFilter::add)}.
 * A filter's {@link IOException} propagates and the filters after it do not run.
 */
public final class ComposingFilter implements IClientHttpRequestFilter {
    private final List<IClientHttpRequestFilter> filters;

    /** A filter that changes nothing. */
    public ComposingFilter() {
        this(List.of());
    }

    private ComposingFilter(List<IClientHttpRequestFilter> filters) {
        this.filters = filters;
    }

    /** Composes the stream's filters in encounter order. */
    public static ComposingFilter of(Stream<? extends IClientHttpRequestFilter> filters) {
        return new ComposingFilter(filters.<IClientHttpRequestFilter>map(filter -> filter).toList());
    }

    /** A new filter that runs this one's filters, then {@code next}. */
    public ComposingFilter add(IClientHttpRequestFilter next) {
        if (next == null) {
            throw new IllegalArgumentException("Filter is required");
        }
        var copy = new ArrayList<>(filters);
        copy.add(next);
        return new ComposingFilter(List.copyOf(copy));
    }

    @Override
    public void filter(MutableHttpRequest request, IClientContext call) throws IOException {
        for (var filter : filters) {
            filter.filter(request, call);
        }
    }

    @Override
    public String toString() {
        return "ComposingFilter" + filters;
    }
}
