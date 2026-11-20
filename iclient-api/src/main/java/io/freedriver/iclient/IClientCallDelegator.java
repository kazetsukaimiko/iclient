package io.freedriver.iclient;

import java.util.function.Supplier;

/** SPI, resolved per method. Wraps the call (retry, logging, metrics …) around the delegate. */
public interface IClientCallDelegator {
    Object invoke(Supplier<Object> delegate, IClientModifier... modifiers) throws IClientCallException;
}
