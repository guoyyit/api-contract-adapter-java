package com.github.guoyyit.apicontractadapterjava.web;

import com.github.guoyyit.apicontractadapterjava.contract.error.ContractConversionException;
import com.github.guoyyit.apicontractadapterjava.contract.error.RuleValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * 统一错误响应：转换失败与规则校验失败都返回结构化错误清单。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ContractConversionException.class)
    public ResponseEntity<Map<String, Object>> handleConversion(ContractConversionException ex) {
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(error -> Map.of("path", error.path(), "reason", error.reason()))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("message", "contract conversion failed", "errors", errors));
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<Map<String, Object>> handleRuleValidation(RuleValidationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "rule set validation failed", "errors", ex.getErrors()));
    }
}
