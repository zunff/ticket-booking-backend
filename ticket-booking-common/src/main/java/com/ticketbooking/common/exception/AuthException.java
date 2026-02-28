package com.ticketbooking.common.exception;

public class AuthException extends RuntimeException {
    private final int code;
    
    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
}
