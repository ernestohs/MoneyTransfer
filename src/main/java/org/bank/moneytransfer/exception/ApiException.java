package org.bank.moneytransfer.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    public ApiException(HttpStatus status, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getDetails() { return details; }
}
