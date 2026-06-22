package ar.edu.unnoba.pdyc2026.usersocial.web;

import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, String>> badRequest(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> asyncError(CompletionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof ResourceNotFoundException notFound) {
            return notFound(notFound);
        }
        if (cause instanceof BusinessRuleException badRequest) {
            return badRequest(badRequest);
        }
        if (cause instanceof DataIntegrityViolationException dive) {
            return dataIntegrity(dive);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected async operation error"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> dataIntegrity(DataIntegrityViolationException ex) {
        String root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        String lower = root == null ? "" : root.toLowerCase();
        String message;
        if (lower.contains("uk_users_username") || lower.contains("users.username") || lower.contains("'username'")) {
            message = "Username is already registered.";
        } else if (lower.contains("uk_users_email") || lower.contains("users.email") || lower.contains("'email'")) {
            message = "Email is already registered.";
        } else {
            message = "Conflict with an existing record.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid request";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }
}
