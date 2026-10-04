package com.github.guoyyit.apicontractadapterjava.contract.model;

import java.util.List;

/**
 * 某一客户端契约的完整规则集合，不可变，可整体热更新。
 */
public final class ContractRuleSet {

    private final String contractId;
    private final long version;
    private final List<FieldRule> rules;
    private final UndeclaredFieldPolicy undeclaredFieldPolicy;

    public ContractRuleSet(String contractId,
                           long version,
                           List<FieldRule> rules,
                           UndeclaredFieldPolicy undeclaredFieldPolicy) {
        this.contractId = contractId;
        this.version = version;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.undeclaredFieldPolicy = undeclaredFieldPolicy == null
                ? UndeclaredFieldPolicy.OMIT : undeclaredFieldPolicy;
    }

    public String contractId() {
        return contractId;
    }

    public long version() {
        return version;
    }

    public List<FieldRule> rules() {
        return rules;
    }

    public UndeclaredFieldPolicy undeclaredFieldPolicy() {
        return undeclaredFieldPolicy;
    }
}
