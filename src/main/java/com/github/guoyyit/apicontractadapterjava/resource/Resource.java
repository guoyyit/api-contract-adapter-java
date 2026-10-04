package com.github.guoyyit.apicontractadapterjava.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Resource {

    private final String id;
    private final long version;
    private final Map<String, Object> attributes;

    public Resource(String id, long version, Map<String, Object> attributes) {
        this.id = id;
        this.version = version;
        this.attributes = deepCopy(attributes);
    }

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
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
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
