package io.freedriver.iclient.api.exception;

/** Retry advice for a retry layer. */
public interface RetryAdvice {
    boolean isRetryable();
}
