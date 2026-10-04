package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.List;

public class RuleValidationException extends RuntimeException {

    private final transient List<RuleError> errors;

    public RuleValidationException(List<RuleError> errors) {
        super("contract rule validation failed: " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<RuleError> getErrors() {
        return errors;
    }
}
