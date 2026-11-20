package io.freedriver.iclient;

import java.util.stream.Stream;

/** A per-proxy option. A composite modifier expands into the modifiers it stands for. */
public interface IClientModifier {
    default Stream<IClientModifier> upgrade() {
        return Stream.of(this);
    }
}
