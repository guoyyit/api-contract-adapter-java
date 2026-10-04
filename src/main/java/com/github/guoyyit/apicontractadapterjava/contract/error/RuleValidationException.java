package com.github.guoyyit.apicontractadapterjava.contract.error;

import java.util.List;

/**
 * 规则集校验失败时抛出，携带完整错误清单；非法规则整体被拒绝加载。
 */
public class RuleValidationException extends RuntimeException {

    private final transient List<String> errors;

    public RuleValidationException(List<String> errors) {
        super("rule set validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
