package io.freedriver.iclient.validation.api;

import io.freedriver.iclient.api.IClientCall;
import io.freedriver.iclient.api.IClientCallDelegator;
import io.freedriver.iclient.api.IClientModifier;
import io.freedriver.iclient.validation.api.exception.InvalidArgumentsCallException;
import io.freedriver.iclient.validation.api.exception.InvalidReturnCallException;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/** Checks arguments before invoking the delegate and marked results afterwards. */
public final class ValidatingCallDelegator implements IClientCallDelegator {
    private final MethodCallValidator calls;
    private final ObjectValidator objects;

    public ValidatingCallDelegator() {
        this(single(ServiceLoader.load(MethodCallValidator.class)), single(ServiceLoader.load(ObjectValidator.class)));
    }

    public ValidatingCallDelegator(MethodCallValidator calls, ObjectValidator objects) {
        this.calls = calls;
        this.objects = objects;
    }

    private static <T> T single(ServiceLoader<T> loader) {
        var providers = loader.stream().toList();
        if (providers.size() > 1) {
            throw new IllegalStateException("Multiple validators: " + providers.stream()
                    .map(provider -> provider.type().getName()).toList());
        }
        return providers.isEmpty() ? null : providers.get(0).get();
    }

    @Override
    public Object invoke(IClientCall call, Supplier<Object> delegate, IClientModifier... modifiers) {
        if (calls == null) {
            return delegate.get();
        }
        var arguments = calls.validate(call.method(), call.args().toArray());
        if (!arguments.isEmpty()) {
            throw new InvalidArgumentsCallException(arguments);
        }
        Object result = delegate.get();
        if (result != null && objects != null && calls.validatesReturnValue(call.method())) {
            var failures = objects.validate(result).stream().map(v -> new Violation(
                    v.path().isEmpty() ? "return" : "return." + v.path(), v.message(), v.constraint())).toList();
            if (!failures.isEmpty()) {
                throw new InvalidReturnCallException(failures, result);
            }
        }
        return result;
    }
}
