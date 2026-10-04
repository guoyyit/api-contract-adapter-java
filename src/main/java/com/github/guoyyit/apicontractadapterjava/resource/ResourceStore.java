package com.github.guoyyit.apicontractadapterjava.resource;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ResourceStore {

    private final ConcurrentHashMap<String, Resource> resources = new ConcurrentHashMap<>();

    public Optional<Resource> get(String id) {
        return Optional.ofNullable(resources.get(id));
    }

    public Resource save(String id, Map<String, Object> attributes) {
        return resources.compute(id, (key, existing) ->
                new Resource(id, existing == null ? 1 : existing.getVersion() + 1, attributes));
    }

    public boolean delete(String id) {
        return resources.remove(id) != null;
    }
}
