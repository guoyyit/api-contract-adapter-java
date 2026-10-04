package com.github.guoyyit.apicontractadapterjava.contract.error;

import java.util.List;

/**
 * 入站/出站转换失败时抛出，携带全部结构化字段错误。
 */
public class ContractConversionException extends RuntimeException {

    private final transient List<FieldConversionError> errors;

    public ContractConversionException(List<FieldConversionError> errors) {
        super("contract conversion failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldConversionError> getErrors() {
        return errors;
    }
}
