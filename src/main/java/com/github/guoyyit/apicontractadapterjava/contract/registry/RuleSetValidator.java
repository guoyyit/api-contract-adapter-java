package com.github.guoyyit.apicontractadapterjava.contract.registry;

import com.github.guoyyit.apicontractadapterjava.contract.engine.TypeConverter;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则集校验器：加载前整体校验，返回完整错误清单。
 */
@Component
public class RuleSetValidator {

    private final TypeConverter typeConverter;

    public RuleSetValidator(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    /**
     * 校验规则集，返回全部错误（空列表表示合法）。
     */
    public List<String> validate(ContractRuleSet ruleSet) {
        List<String> errors = new ArrayList<>();
        if (ruleSet == null) {
            errors.add("rule set must not be null");
            return errors;
        }
        if (ruleSet.contractId() == null || ruleSet.contractId().isBlank()) {
            errors.add("contractId must not be blank");
        }
        Set<String> sourcePaths = new HashSet<>();
        Set<String> targetPaths = new HashSet<>();
        List<FieldRule> rules = ruleSet.rules();
        for (int i = 0; i < rules.size(); i++) {
            FieldRule rule = rules.get(i);
            String label = "rules[" + i + "]";
            if (rule == null) {
                errors.add(label + ": rule must not be null");
                continue;
            }
            validatePath(rule.sourcePath(), label + ".sourcePath", errors);
            validatePath(rule.targetPath(), label + ".targetPath", errors);
            if (rule.type() != null && !typeConverter.supports(rule.type())) {
                errors.add(label + ".type: unsupported type '" + rule.type() + "'");
            }
            if (rule.sourcePath() != null && !sourcePaths.add(rule.sourcePath())) {
                errors.add(label + ".sourcePath: duplicate source path '" + rule.sourcePath() + "'");
            }
            if (rule.targetPath() != null && !targetPaths.add(rule.targetPath())) {
                errors.add(label + ".targetPath: duplicate target path '" + rule.targetPath() + "'");
            }
        }
        return errors;
    }

    private void validatePath(String path, String label, List<String> errors) {
        if (path == null || path.isBlank()) {
            errors.add(label + ": path must not be blank");
            return;
        }
        for (String segment : path.split("\\.", -1)) {
            if (segment.isEmpty()) {
                errors.add(label + ": path '" + path + "' contains an empty segment");
                return;
            }
            String name = segment.endsWith("[]")
                    ? segment.substring(0, segment.length() - 2) : segment;
            if (name.isEmpty()) {
                errors.add(label + ": path '" + path + "' contains an invalid segment '" + segment + "'");
                return;
            }
        }
    }
}
