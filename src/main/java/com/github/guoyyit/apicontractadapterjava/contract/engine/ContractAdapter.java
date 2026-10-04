package com.github.guoyyit.apicontractadapterjava.contract.engine;

import com.github.guoyyit.apicontractadapterjava.contract.error.ContractConversionException;
import com.github.guoyyit.apicontractadapterjava.contract.error.FieldConversionError;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.registry.ContractRuleRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约适配引擎：请求与响应按客户端绑定契约做双向转换。
 *
 * <p>每次转换只在开始时取一次规则集引用，热更新不会在单次转换中混入新旧规则；
 * 转换为纯函数，不修改输入，同一数据在同一请求内多次转换结果完全一致。
 */
@Component
public class ContractAdapter {

    /** 缺失哨兵：用于区分“字段缺失”与“字段为空值 null”。 */
    private static final Object MISSING = new Object();

    private final ContractRuleRegistry ruleRegistry;
    private final TypeConverter typeConverter;

    public ContractAdapter(ContractRuleRegistry ruleRegistry, TypeConverter typeConverter) {
        this.ruleRegistry = ruleRegistry;
        this.typeConverter = typeConverter;
    }

    /**
     * 入站：客户端契约视图 -> 内部资源模型。
     * 任何无法映射或转换失败的字段都产生结构化错误并整体失败。
     */
    public Map<String, Object> toInternal(String contractId, Map<String, Object> clientView) {
        ContractRuleSet ruleSet = requireRuleSet(contractId);
        List<FieldConversionError> errors = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldRule rule : ruleSet.rules()) {
            Object raw = extract(clientView, split(rule.sourcePath()), 0, rule.sourcePath(), errors);
            Object processed = processValue(raw, rule, rule.sourcePath(), errors);
            if (processed != MISSING) {
                write(out, split(rule.targetPath()), 0, processed);
            }
        }
        if (!errors.isEmpty()) {
            throw new ContractConversionException(errors);
        }
        return out;
    }

    /**
     * 出站：内部资源模型 -> 客户端契约视图。
     * 未声明字段按规则集配置的策略处理。
     */
    public Map<String, Object> toClientView(String contractId, Map<String, Object> internalModel) {
        ContractRuleSet ruleSet = requireRuleSet(contractId);
        List<FieldConversionError> errors = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldRule rule : ruleSet.rules()) {
            Object raw = extract(internalModel, split(rule.targetPath()), 0, rule.sourcePath(), errors);
            Object processed = processValue(raw, rule, rule.sourcePath(), errors);
            if (processed != MISSING) {
                write(out, split(rule.sourcePath()), 0, processed);
            }
        }
        applyUndeclaredPolicy(ruleSet, internalModel, out, errors);
        if (!errors.isEmpty()) {
            throw new ContractConversionException(errors);
        }
        return out;
    }

    private ContractRuleSet requireRuleSet(String contractId) {
        return ruleRegistry.currentRules(contractId)
                .orElseThrow(() -> new ContractConversionException(List.of(
                        new FieldConversionError("", "no rule set bound to contract: " + contractId))));
    }

    private void applyUndeclaredPolicy(ContractRuleSet ruleSet,
                                       Map<String, Object> internalModel,
                                       Map<String, Object> out,
                                       List<FieldConversionError> errors) {
        Set<String> declaredTopSegments = new LinkedHashSet<>();
        for (FieldRule rule : ruleSet.rules()) {
            declaredTopSegments.add(segmentName(split(rule.targetPath())[0]));
        }
        for (Map.Entry<String, Object> entry : internalModel.entrySet()) {
            if (declaredTopSegments.contains(entry.getKey())) {
                continue;
            }
            switch (ruleSet.undeclaredFieldPolicy()) {
                case EXPOSE -> out.putIfAbsent(entry.getKey(), deepCopy(entry.getValue()));
                case OMIT -> {
                    // 未声明字段不暴露
                }
                case FAIL -> errors.add(new FieldConversionError(
                        entry.getKey(), "undeclared field rejected by policy FAIL"));
            }
        }
    }

    /**
     * 缺失/空值/类型转换处理：缺失走默认值与必填校验，空值 null 原样保留，
     * 集合逐元素递归处理且保持顺序。
     */
    private Object processValue(Object raw, FieldRule rule, String path,
                                List<FieldConversionError> errors) {
        if (raw == MISSING) {
            if (rule.defaultValue() != null) {
                return convertLeaf(deepCopy(rule.defaultValue()), rule, path, errors);
            }
            if (rule.required()) {
                errors.add(new FieldConversionError(path, "required field is missing"));
            }
            return MISSING;
        }
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                out.add(processValue(element, rule, path, errors));
            }
            return out;
        }
        if (raw == null) {
            return null;
        }
        return convertLeaf(raw, rule, path, errors);
    }

    private Object convertLeaf(Object value, FieldRule rule, String path,
                               List<FieldConversionError> errors) {
        if (rule.type() == null) {
            return value;
        }
        try {
            return typeConverter.convert(value, rule.type());
        } catch (IllegalArgumentException e) {
            errors.add(new FieldConversionError(path, e.getMessage()));
            return MISSING;
        }
    }

    /**
     * 按路径提取值；路径不存在返回 MISSING，中间结构类型不匹配记录错误并返回 MISSING。
     */
    private Object extract(Object node, String[] segments, int index, String path,
                           List<FieldConversionError> errors) {
        if (index == segments.length) {
            return node == null ? null : deepCopy(node);
        }
        String segment = segments[index];
        if (segment.endsWith("[]")) {
            String name = segmentName(segment);
            if (node == null) {
                return MISSING;
            }
            if (!(node instanceof Map<?, ?> map)) {
                errors.add(new FieldConversionError(path,
                        "expected an object at segment '" + segment + "' but found "
                                + typeName(node)));
                return MISSING;
            }
            if (!map.containsKey(name)) {
                return MISSING;
            }
            Object child = map.get(name);
            if (child == null) {
                return null;
            }
            if (!(child instanceof List<?> list)) {
                errors.add(new FieldConversionError(path,
                        "expected a collection at segment '" + segment + "' but found "
                                + typeName(child)));
                return MISSING;
            }
            List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                out.add(extract(element, segments, index + 1, path, errors));
            }
            return out;
        }
        if (node == null) {
            return MISSING;
        }
        if (!(node instanceof Map<?, ?> map)) {
            errors.add(new FieldConversionError(path,
                    "expected an object at segment '" + segment + "' but found " + typeName(node)));
            return MISSING;
        }
        if (!map.containsKey(segment)) {
            return MISSING;
        }
        return extract(map.get(segment), segments, index + 1, path, errors);
    }

    /**
     * 按路径写入值，递归创建嵌套结构；集合按索引对齐写入，保持顺序、不引入重复元素。
     */
    @SuppressWarnings("unchecked")
    private void write(Object container, String[] segments, int index, Object value) {
        String segment = segments[index];
        boolean last = index == segments.length - 1;
        Map<String, Object> map = (Map<String, Object>) container;
        if (segment.endsWith("[]")) {
            String name = segmentName(segment);
            List<Object> values = (List<Object>) value;
            if (last) {
                map.put(name, deepCopy(values));
                return;
            }
            List<Object> children = (List<Object>) map.computeIfAbsent(name, key -> {
                List<Object> created = new ArrayList<>(values.size());
                for (int i = 0; i < values.size(); i++) {
                    created.add(new LinkedHashMap<String, Object>());
                }
                return created;
            });
            for (int i = 0; i < values.size(); i++) {
                Object element = values.get(i);
                if (element != MISSING) {
                    write(children.get(i), segments, index + 1, element);
                }
            }
            return;
        }
        if (last) {
            map.put(segment, value);
            return;
        }
        Object child = map.computeIfAbsent(segment, key -> new LinkedHashMap<String, Object>());
        write(child, segments, index + 1, value);
    }

    private static String[] split(String path) {
        return path.split("\\.", -1);
    }

    private static String segmentName(String segment) {
        return segment.endsWith("[]") ? segment.substring(0, segment.length() - 2) : segment;
    }

    private static String typeName(Object node) {
        return node == null ? "null" : node.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put((String) entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        return value;
    }
}
