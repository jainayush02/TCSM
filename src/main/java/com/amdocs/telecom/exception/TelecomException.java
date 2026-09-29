package com.amdocs.telecom.exception;

public class TelecomException extends Exception {
    private static final long serialVersionUID = 1L;

    public TelecomException(String message) {
        super(message);
    }

    public TelecomException(String message, Throwable cause) {
        super(message, cause);
    }
}
