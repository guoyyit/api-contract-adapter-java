package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.ArrayList;
import java.util.List;

public class ContractRuleSet {

    private String id;
    private List<FieldRule> rules = new ArrayList<>();
    private UndeclaredFieldPolicy inboundPolicy = UndeclaredFieldPolicy.REJECT;
    private UndeclaredFieldPolicy outboundPolicy = UndeclaredFieldPolicy.HIDE;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<FieldRule> getRules() {
        return rules;
    }

    public void setRules(List<FieldRule> rules) {
        this.rules = rules;
    }

    public UndeclaredFieldPolicy getInboundPolicy() {
        return inboundPolicy;
    }

    public void setInboundPolicy(UndeclaredFieldPolicy inboundPolicy) {
        this.inboundPolicy = inboundPolicy;
    }

    public UndeclaredFieldPolicy getOutboundPolicy() {
        return outboundPolicy;
    }

    public void setOutboundPolicy(UndeclaredFieldPolicy outboundPolicy) {
        this.outboundPolicy = outboundPolicy;
    }
}
