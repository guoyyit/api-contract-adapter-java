package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContractConfigParser {

    private ContractConfigParser() {
    }

    public record ParseResult(ContractSnapshot snapshot, List<RuleError> errors) {
    }

    public static ParseResult parse(Map<String, Object> rawConfig) {
        List<RuleError> errors = new ArrayList<>();
        Map<String, ContractRuleSet> contracts = new LinkedHashMap<>();
        Map<String, String> bindings = new LinkedHashMap<>();
        if (rawConfig == null) {
            errors.add(new RuleError("$", "config body must not be null"));
            return new ParseResult(new ContractSnapshot(contracts, bindings), errors);
        }
        Object contractsNode = rawConfig.get("contracts");
        if (!(contractsNode instanceof Map<?, ?> contractsMap)) {
            errors.add(new RuleError("contracts", "contracts must be an object of contract definitions"));
        } else {
            for (Map.Entry<?, ?> entry : contractsMap.entrySet()) {
                String contractId = String.valueOf(entry.getKey());
                String base = "contracts." + contractId;
                if (!(entry.getValue() instanceof Map<?, ?> contractMap)) {
                    errors.add(new RuleError(base, "contract definition must be an object"));
                    continue;
                }
                contracts.put(contractId, parseContract(contractId, castMap(contractMap), base, errors));
            }
        }
        Object bindingsNode = rawConfig.get("bindings");
        if (bindingsNode != null) {
            if (!(bindingsNode instanceof Map<?, ?> bindingsMap)) {
                errors.add(new RuleError("bindings", "bindings must be an object of clientId -> contractId"));
            } else {
                for (Map.Entry<?, ?> entry : bindingsMap.entrySet()) {
                    String clientId = String.valueOf(entry.getKey());
                    if (!(entry.getValue() instanceof String contractId)) {
                        errors.add(new RuleError("bindings." + clientId,
                                "binding value must be a contract id string"));
                        continue;
                    }
                    bindings.put(clientId, contractId);
                }
            }
        }
        return new ParseResult(new ContractSnapshot(contracts, bindings), errors);
    }

    private static ContractRuleSet parseContract(String id, Map<String, Object> node, String base,
                                                 List<RuleError> errors) {
        ContractRuleSet ruleSet = new ContractRuleSet();
        ruleSet.setId(id);
        UndeclaredFieldPolicy inbound = parsePolicy(node.get("inboundPolicy"),
                base + ".inboundPolicy", errors);
        if (inbound != null) {
            ruleSet.setInboundPolicy(inbound);
        }
        UndeclaredFieldPolicy outbound = parsePolicy(node.get("outboundPolicy"),
                base + ".outboundPolicy", errors);
        if (outbound != null) {
            ruleSet.setOutboundPolicy(outbound);
        }
        ruleSet.setRules(parseRules(node.get("rules"), base + ".rules", errors));
        return ruleSet;
    }

    private static List<FieldRule> parseRules(Object node, String path, List<RuleError> errors) {
        List<FieldRule> rules = new ArrayList<>();
        if (node == null) {
            return rules;
        }
        if (!(node instanceof List<?> list)) {
            errors.add(new RuleError(path, "rules must be an array"));
            return rules;
        }
        for (int i = 0; i < list.size(); i++) {
            String base = path + "[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> ruleMap)) {
                errors.add(new RuleError(base, "rule must be an object"));
                continue;
            }
            rules.add(parseRule(castMap(ruleMap), base, errors));
        }
        return rules;
    }

    private static FieldRule parseRule(Map<String, Object> node, String base, List<RuleError> errors) {
        FieldRule rule = new FieldRule();
        rule.setSource(asString(node.get("source"), base + ".source", errors));
        rule.setTarget(asString(node.get("target"), base + ".target", errors));
        Object conversionNode = node.get("conversion");
        if (conversionNode != null) {
            String name = asString(conversionNode, base + ".conversion", errors);
            if (name != null) {
                try {
                    rule.setConversion(TypeConversion.valueOf(name.trim().toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    errors.add(new RuleError(base + ".conversion",
                            "unknown conversion: " + name));
                }
            }
        }
        Object requiredNode = node.get("required");
        if (requiredNode != null) {
            if (requiredNode instanceof Boolean required) {
                rule.setRequired(required);
            } else {
                errors.add(new RuleError(base + ".required", "required must be a boolean"));
            }
        }
        if (node.containsKey("defaultValue")) {
            rule.setHasDefault(true);
            rule.setDefaultValue(node.get("defaultValue"));
        }
        rule.setChildren(parseRules(node.get("children"), base + ".children", errors));
        return rule;
    }

    private static UndeclaredFieldPolicy parsePolicy(Object node, String path, List<RuleError> errors) {
        if (node == null) {
            return null;
        }
        String name = asString(node, path, errors);
        if (name == null) {
            return null;
        }
        try {
            return UndeclaredFieldPolicy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            errors.add(new RuleError(path, "unknown policy: " + name));
            return null;
        }
    }

    private static String asString(Object node, String path, List<RuleError> errors) {
        if (node == null) {
            return null;
        }
        if (!(node instanceof String text)) {
            errors.add(new RuleError(path, "must be a string"));
            return null;
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
