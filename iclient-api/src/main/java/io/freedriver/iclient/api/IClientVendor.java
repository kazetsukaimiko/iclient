package io.freedriver.iclient.api;

import io.freedriver.iclient.api.exception.IClientConstructionException;
import java.util.Optional;

/** Supplies the raw client for a supported interface, or empty to try the next vendor. */
public interface IClientVendor {
    <P> Optional<P> createClient(Class<P> apiClass, IClientConfig config, IClientModifier... modifiers)
            throws IClientConstructionException;
}
