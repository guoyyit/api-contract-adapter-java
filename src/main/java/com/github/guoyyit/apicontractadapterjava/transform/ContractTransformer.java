package com.github.guoyyit.apicontractadapterjava.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.github.guoyyit.apicontractadapterjava.contract.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.UndeclaredFieldPolicy;
import org.springframework.stereotype.Component;

@Component
public class ContractTransformer {

    public Map<String, Object> inbound(ContractRuleSet ruleSet, Map<String, Object> clientDocument) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, Object> result = mapObject(ruleSet.getRules(), clientDocument,
                ruleSet.getInboundPolicy(), true, errors, "");
        if (!errors.isEmpty()) {
            throw new TransformException(errors);
        }
        return result;
    }

    public Map<String, Object> outbound(ContractRuleSet ruleSet, Map<String, Object> internalAttributes) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, Object> result = mapObject(ruleSet.getRules(), internalAttributes,
                ruleSet.getOutboundPolicy(), false, errors, "");
        if (!errors.isEmpty()) {
            throw new TransformException(errors);
        }
        return result;
    }

    private Map<String, Object> mapObject(List<FieldRule> rules, Map<String, Object> sourceDoc,
                                          UndeclaredFieldPolicy policy, boolean inbound,
                                          List<FieldError> errors, String prefix) {
        Map<String, Object> target = new LinkedHashMap<>();
        Set<String> declaredKeys = new TreeSet<>();
        for (FieldRule rule : rules) {
            String from = inbound ? rule.getSource() : rule.getTarget();
            String to = inbound ? rule.getTarget() : rule.getSource();
            List<String> fromSegments = PathAccessor.parse(from);
            declaredKeys.add(fromSegments.get(0));
            String fieldPath = prefix + from;
            if (!PathAccessor.exists(sourceDoc, fromSegments)) {
                if (rule.hasDefault()) {
                    Object value = applyConversion(rule.getDefaultValue(), rule, fieldPath, errors);
                    setChecked(target, to, value, fieldPath, errors);
                } else if (rule.isRequired()) {
                    errors.add(new FieldError(fieldPath, FieldError.FIELD_MISSING,
                            "required field is missing and no default is declared"));
                }
                continue;
            }
            Object value = PathAccessor.get(sourceDoc, fromSegments);
            if (value == null) {
                setChecked(target, to, null, fieldPath, errors);
                continue;
            }
            boolean hasChildren = rule.getChildren() != null && !rule.getChildren().isEmpty();
            if (hasChildren) {
                mapContainer(rule, value, policy, inbound, errors, fieldPath, target, to);
            } else {
                Object converted = applyConversion(copyValue(value), rule, fieldPath, errors);
                setChecked(target, to, converted, fieldPath, errors);
            }
        }
        applyUndeclaredPolicy(sourceDoc, target, declaredKeys, policy, errors, prefix);
        return target;
    }

    private void mapContainer(FieldRule rule, Object value, UndeclaredFieldPolicy policy,
                              boolean inbound, List<FieldError> errors, String fieldPath,
                              Map<String, Object> target, String to) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> childSource = (Map<String, Object>) map;
            Map<String, Object> childResult = mapObject(rule.getChildren(), childSource,
                    policy, inbound, errors, fieldPath + ".");
            setChecked(target, to, childResult, fieldPath, errors);
            return;
        }
        if (value instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                String elementPath = fieldPath + "[" + i + "]";
                if (element == null) {
                    mapped.add(null);
                } else if (element instanceof Map<?, ?> elementMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> elementSource = (Map<String, Object>) elementMap;
                    mapped.add(mapObject(rule.getChildren(), elementSource, policy, inbound,
                            errors, elementPath + "."));
                } else {
                    errors.add(new FieldError(elementPath, FieldError.TYPE_MISMATCH,
                            "expected an object element but found "
                                    + element.getClass().getSimpleName()));
                    mapped.add(null);
                }
            }
            setChecked(target, to, mapped, fieldPath, errors);
            return;
        }
        errors.add(new FieldError(fieldPath, FieldError.TYPE_MISMATCH,
                "expected an object or an array of objects but found "
                        + value.getClass().getSimpleName()));
    }

    private void applyUndeclaredPolicy(Map<String, Object> sourceDoc, Map<String, Object> target,
                                       Set<String> declaredKeys, UndeclaredFieldPolicy policy,
                                       List<FieldError> errors, String prefix) {
        Set<String> undeclared = new TreeSet<>(sourceDoc.keySet());
        undeclared.removeAll(declaredKeys);
        for (String key : undeclared) {
            switch (policy) {
                case REJECT -> errors.add(new FieldError(prefix + key, FieldError.UNDECLARED_FIELD,
                        "field is not declared in the contract"));
                case EXPOSE -> target.put(key, copyValue(sourceDoc.get(key)));
                case IGNORE, HIDE -> {
                }
            }
        }
    }

    private Object applyConversion(Object value, FieldRule rule, String fieldPath,
                                   List<FieldError> errors) {
        try {
            return TypeConverter.convert(value, rule.getConversion(), fieldPath);
        } catch (TypeConverter.ConversionException ex) {
            errors.add(new FieldError(fieldPath, FieldError.CONVERSION_FAILED, ex.getMessage()));
            return null;
        }
    }

    private void setChecked(Map<String, Object> target, String to, Object value, String fieldPath,
                            List<FieldError> errors) {
        try {
            PathAccessor.set(target, PathAccessor.parse(to), value);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            errors.add(new FieldError(fieldPath, FieldError.TYPE_MISMATCH,
                    "cannot write target path '" + to + "': " + ex.getMessage()));
        }
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        return value;
    }
}
