package io.freedriver.iclient.api;

import io.freedriver.iclient.api.exception.IClientCallException;
import java.util.function.Supplier;

/** SPI, resolved per method. Wraps the call (retry, logging, metrics …) around the delegate. */
public interface IClientCallDelegator {
    Object invoke(Supplier<Object> delegate, IClientModifier... modifiers) throws IClientCallException;
}
