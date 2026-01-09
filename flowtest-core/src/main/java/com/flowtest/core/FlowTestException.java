package com.flowtest.core;

/**
 * Exception thrown by FlowTest framework.
 */
public class FlowTestException extends RuntimeException {

    public FlowTestException(String message) {
        super(message);
    }

    public FlowTestException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowTestException(Throwable cause) {
        super(cause);
    }
}
