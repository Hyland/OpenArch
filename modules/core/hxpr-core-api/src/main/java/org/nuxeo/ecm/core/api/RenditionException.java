package org.nuxeo.ecm.core.api;

public class RenditionException extends RuntimeException {

    protected final int statusCode;

    public RenditionException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public RenditionException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
