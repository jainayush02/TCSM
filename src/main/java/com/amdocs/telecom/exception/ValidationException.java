package com.amdocs.telecom.exception;

public class ValidationException extends TelecomException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
