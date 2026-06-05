package org.bank.moneytransfer.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bank.moneytransfer.config.CorrelationIds;
import org.bank.moneytransfer.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        CorrelationIds.current(request),
                        exception.getDetails()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "The request body failed validation.", CorrelationIds.current(request), details));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.", CorrelationIds.current(request), Map.of()));
    }
}
