package io.freedriver.iclient.http.api.call;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Header collections preserve HTTP's case-insensitive field-name lookup. */
final class Headers {
    private Headers() {}

    static Map<String, List<String>> mutable() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    static Map<String, List<String>> snapshot(Map<String, List<String>> source) {
        var copy = mutable();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
