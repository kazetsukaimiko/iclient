package io.freedriver.iclient.api;

import io.freedriver.iclient.api.exception.IClientConstructionException;
/**
 * Builds thread-safe proxies that retain only construction inputs and hold no mutable state.
 * The target must be an interface. Before resolution, expand each supplied modifier once
 * with {@link IClientModifier#upgrade()} and pass the flattened list to factories and delegators.
 * Resolve every abstract method, including inherited methods, during construction.
 * Throw {@link IClientConstructionException} for a non-interface or a missing handler,
 * naming the unresolved method in the latter case.
 * Unclaimed default methods run via {@link java.lang.reflect.InvocationHandler#invokeDefault}.
 * The proxy implements equals and hashCode by identity and toString with the interface name.
 * Handler factories never handle these three methods.
 */
public interface IClientProxyFactory {
    <T> T create(Class<T> interfaceClass, IClientConfig config,
            IClientModifier... modifiers) throws IClientConstructionException;
}
