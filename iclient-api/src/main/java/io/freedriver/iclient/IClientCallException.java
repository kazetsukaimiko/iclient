package io.freedriver.iclient;

/** Failure while invoking a client method. */
public class IClientCallException extends RuntimeException {
    public IClientCallException() {
        super();
    }

    public IClientCallException(String message) {
        super(message);
    }

    public IClientCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public IClientCallException(Throwable cause) {
        super(cause);
    }
}
