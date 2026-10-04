package com.github.guoyyit.apicontractadapterjava.web;

import java.util.List;
import java.util.Map;

import com.github.guoyyit.apicontractadapterjava.contract.RuleValidationException;
import com.github.guoyyit.apicontractadapterjava.contract.UnknownContractException;
import com.github.guoyyit.apicontractadapterjava.transform.TransformException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TransformException.class)
    public ResponseEntity<Map<String, Object>> handleTransform(TransformException ex) {
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(error -> Map.of(
                        "path", error.path(),
                        "code", error.code(),
                        "message", error.message()))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("errors", errors));
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<Map<String, Object>> handleRuleValidation(RuleValidationException ex) {
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(error -> Map.of(
                        "path", error.path(),
                        "message", error.message()))
                .toList();
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    @ExceptionHandler(UnknownContractException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownContract(UnknownContractException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
