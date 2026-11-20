package io.freedriver.iclient.api.exception;

/** Retry advice for a call delegator. */
public interface RetryAdvice {
    boolean isRetryable();
}
