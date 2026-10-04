package com.github.guoyyit.apicontractadapterjava.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内部资源模型存储：并发安全，读取返回一致性快照。
 *
 * <p>写入与读取都做深拷贝，任一客户端读到的快照对应某一时刻完整的资源状态，
 * 并发修改不会渗入已返回的快照。
 */
@Component
public class ResourceStore {

    private final ConcurrentMap<String, Map<String, Object>> resources = new ConcurrentHashMap<>();

    /**
     * 读取资源的一致性快照。
     */
    public Optional<Map<String, Object>> snapshot(String resourceId) {
        Map<String, Object> current = resources.get(resourceId);
        return current == null ? Optional.empty() : Optional.of(deepCopy(current));
    }

    /**
     * 以内部模型整体替换资源。
     */
    public void put(String resourceId, Map<String, Object> internalModel) {
        resources.put(resourceId, deepCopy(internalModel));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopy((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopyValue(element));
            }
            return copy;
        }
        return value;
    }
}
