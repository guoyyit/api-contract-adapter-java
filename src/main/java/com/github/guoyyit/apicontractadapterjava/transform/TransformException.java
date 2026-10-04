package com.github.guoyyit.apicontractadapterjava.transform;

import java.util.List;

public class TransformException extends RuntimeException {

    private final transient List<FieldError> errors;

    public TransformException(List<FieldError> errors) {
        super("contract transformation failed: " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
