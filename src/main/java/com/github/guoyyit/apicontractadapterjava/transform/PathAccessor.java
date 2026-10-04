package com.github.guoyyit.apicontractadapterjava.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PathAccessor {

    private PathAccessor() {
    }

    public static List<String> parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        String[] parts = path.split("\\.", -1);
        List<String> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("path contains an empty segment: " + path);
            }
            segments.add(part);
        }
        return segments;
    }

    public static boolean exists(Map<String, Object> document, List<String> segments) {
        Map<String, Object> current = document;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (!current.containsKey(segment)) {
                return false;
            }
            if (i == segments.size() - 1) {
                return true;
            }
            Object next = current.get(segment);
            if (!(next instanceof Map<?, ?> map)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nextMap = (Map<String, Object>) map;
            current = nextMap;
        }
        return true;
    }

    public static Object get(Map<String, Object> document, List<String> segments) {
        Map<String, Object> current = document;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (!current.containsKey(segment)) {
                return null;
            }
            Object value = current.get(segment);
            if (i == segments.size() - 1) {
                return value;
            }
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nextMap = (Map<String, Object>) map;
            current = nextMap;
        }
        return null;
    }

    public static void set(Map<String, Object> document, List<String> segments, Object value) {
        Map<String, Object> current = document;
        for (int i = 0; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            Object next = current.get(segment);
            if (next == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segment, created);
                current = created;
            } else if (next instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nextMap = (Map<String, Object>) map;
                current = nextMap;
            } else {
                throw new IllegalStateException(
                        "path segment '" + segment + "' already holds a non-object value");
            }
        }
        current.put(segments.get(segments.size() - 1), value);
    }
}
