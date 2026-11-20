package io.freedriver.iclient.api;

import java.util.stream.Stream;

/** A per-proxy option. A composite modifier expands into the modifiers it stands for. */
public interface IClientModifier {
    default Stream<IClientModifier> upgrade() {
        return Stream.of(this);
    }
}
