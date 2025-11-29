package com.whoshot.exception;

public class ApiClientException extends RuntimeException {

    public ApiClientException(String message) {
        super(message);
    }

    public ApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
