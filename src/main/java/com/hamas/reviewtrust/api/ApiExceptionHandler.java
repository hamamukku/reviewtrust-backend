package com.hamas.reviewtrust.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("ResponseStatusException", ex);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> err = new HashMap<>();
        err.put("code", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        body.put("error", err);
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> err = new HashMap<>();
        err.put("code", "E_SERVER");
        err.put("message", ex.getMessage());
        body.put("error", err);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
