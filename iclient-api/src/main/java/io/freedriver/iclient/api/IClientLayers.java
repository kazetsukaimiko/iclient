package io.freedriver.iclient.api;

/** Standard ordering slots, from outermost to innermost. */
public final class IClientLayers {
    public static final int CONTEXT = 0;
    public static final int VALIDATION = 100;
    public static final int RESILIENCY = 200;
    public static final int METRICS = 300;
    public static final int EXCEPTION_MAPPING = 900;

    private IClientLayers() {}
}
