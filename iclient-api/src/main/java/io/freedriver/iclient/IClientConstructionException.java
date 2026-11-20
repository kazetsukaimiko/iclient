package io.freedriver.iclient;

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
