package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.exception.ProjectNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * This @RestControllerAdvice intercepts all responses from @RestController
 * classes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<HttpResponse<Void>> handleResourceNotFound(ProjectNotFoundException ex) {
        HttpResponse<Void> errorResponse = HttpResponse.error(
                "Resource Not Found",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<HttpResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.error("No resource found exception: ", ex);
        HttpResponse<Void> errorResponse = HttpResponse.error("Resource Not Found");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("Validation error: {}", errorMessage);
        HttpResponse<Void> errorResponse = HttpResponse.error(
                "Validation Failed",
                errorMessage
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<HttpResponse<Void>> handleWrongHttpVerb(HttpRequestMethodNotSupportedException ex) {
        log.error("Wrong HTTP verb: {}", ex.getMessage());
        HttpResponse<Void> errorResponse = HttpResponse.error(
                "Method Not Allowed",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles:
     * 1. Missing Request Body (Body is null/empty)
     * 2. Malformed JSON (Syntax errors, missing brackets, etc.)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<HttpResponse<Void>> handleMalformedRequest(HttpMessageNotReadableException ex) {
        log.error("Malformed request: ", ex);

        HttpResponse<Void> errorResponse = HttpResponse.error("Bad Request");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles:
     * User sends "Content-Type: text/plain" or "application/xml"
     * but your API expects "application/json".
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<HttpResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported Media Type: {}", ex.getContentType());

        HttpResponse<Void> errorResponse = HttpResponse.error(
                "Unsupported Media Type",
                "API only accepts JSON. Please set 'Content-Type: application/json'"
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<HttpResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument exception: ", ex);
        HttpResponse<Void> errorResponse = HttpResponse.error(
                "Invalid Request",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        HttpResponse<Void> errorResponse = HttpResponse.error(
                "An internal server error occurred",
                "Please contact support."
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}