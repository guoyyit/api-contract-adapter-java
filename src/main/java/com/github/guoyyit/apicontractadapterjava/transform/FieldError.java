package com.github.guoyyit.apicontractadapterjava.transform;

public record FieldError(String path, String code, String message) {

    public static final String FIELD_MISSING = "FIELD_MISSING";
    public static final String CONVERSION_FAILED = "CONVERSION_FAILED";
    public static final String UNDECLARED_FIELD = "UNDECLARED_FIELD";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
}
