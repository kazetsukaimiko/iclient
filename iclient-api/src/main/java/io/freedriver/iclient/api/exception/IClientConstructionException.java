package io.freedriver.iclient.api.exception;

/** Failure while constructing a client proxy. */
public class IClientConstructionException extends Exception {
    public IClientConstructionException() {
        super();
    }

    public IClientConstructionException(String message) {
        super(message);
    }

    public IClientConstructionException(String message, Throwable cause) {
        super(message, cause);
    }

    public IClientConstructionException(Throwable cause) {
        super(cause);
    }
}
