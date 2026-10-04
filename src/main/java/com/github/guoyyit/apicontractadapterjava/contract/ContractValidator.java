package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.guoyyit.apicontractadapterjava.transform.PathAccessor;
import com.github.guoyyit.apicontractadapterjava.transform.TypeConverter;

public final class ContractValidator {

    private ContractValidator() {
    }

    public static List<RuleError> validate(ContractSnapshot snapshot) {
        List<RuleError> errors = new ArrayList<>();
        for (Map.Entry<String, ContractRuleSet> entry : snapshot.getContracts().entrySet()) {
            String contractId = entry.getKey();
            ContractRuleSet ruleSet = entry.getValue();
            String base = "contracts." + contractId;
            if (contractId == null || contractId.isBlank()) {
                errors.add(new RuleError("contracts", "contract id must not be blank"));
            }
            if (ruleSet.getInboundPolicy() != UndeclaredFieldPolicy.REJECT
                    && ruleSet.getInboundPolicy() != UndeclaredFieldPolicy.IGNORE) {
                errors.add(new RuleError(base + ".inboundPolicy",
                        "inboundPolicy must be REJECT or IGNORE"));
            }
            if (ruleSet.getOutboundPolicy() != UndeclaredFieldPolicy.EXPOSE
                    && ruleSet.getOutboundPolicy() != UndeclaredFieldPolicy.HIDE) {
                errors.add(new RuleError(base + ".outboundPolicy",
                        "outboundPolicy must be EXPOSE or HIDE"));
            }
            validateRules(ruleSet.getRules(), base + ".rules", errors);
        }
        for (Map.Entry<String, String> binding : snapshot.getClientBindings().entrySet()) {
            String base = "bindings." + binding.getKey();
            if (binding.getKey() == null || binding.getKey().isBlank()) {
                errors.add(new RuleError("bindings", "client id must not be blank"));
            }
            if (!snapshot.getContracts().containsKey(binding.getValue())) {
                errors.add(new RuleError(base,
                        "binding references unknown contract: " + binding.getValue()));
            }
        }
        return errors;
    }

    private static void validateRules(List<FieldRule> rules, String path, List<RuleError> errors) {
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            FieldRule rule = rules.get(i);
            String base = path + "[" + i + "]";
            validatePath(rule.getSource(), base + ".source", errors);
            validatePath(rule.getTarget(), base + ".target", errors);
            if (rule.getSource() != null && !sources.add(rule.getSource())) {
                errors.add(new RuleError(base + ".source",
                        "duplicate source path among sibling rules: " + rule.getSource()));
            }
            if (rule.getTarget() != null && !targets.add(rule.getTarget())) {
                errors.add(new RuleError(base + ".target",
                        "duplicate target path among sibling rules: " + rule.getTarget()));
            }
            if (rule.getConversion() == null) {
                errors.add(new RuleError(base + ".conversion", "conversion must not be null"));
            }
            boolean hasChildren = rule.getChildren() != null && !rule.getChildren().isEmpty();
            if (hasChildren && rule.getConversion() != null
                    && rule.getConversion() != TypeConversion.NONE) {
                errors.add(new RuleError(base + ".conversion",
                        "a rule with nested children must not declare a conversion"));
            }
            if (rule.hasDefault() && rule.getConversion() != null
                    && rule.getConversion() != TypeConversion.NONE) {
                try {
                    TypeConverter.convert(rule.getDefaultValue(), rule.getConversion(),
                            rule.getTarget());
                } catch (TypeConverter.ConversionException ex) {
                    errors.add(new RuleError(base + ".defaultValue",
                            "default value is not convertible: " + ex.getMessage()));
                }
            }
            if (hasChildren) {
                validateRules(rule.getChildren(), base + ".children", errors);
            }
        }
    }

    private static void validatePath(String path, String errorPath, List<RuleError> errors) {
        if (path == null || path.isBlank()) {
            errors.add(new RuleError(errorPath, "path must not be blank"));
            return;
        }
        try {
            PathAccessor.parse(path);
        } catch (IllegalArgumentException ex) {
            errors.add(new RuleError(errorPath, "invalid path: " + ex.getMessage()));
        }
    }
}
