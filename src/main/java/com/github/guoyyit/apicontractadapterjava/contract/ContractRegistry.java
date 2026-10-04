package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.github.guoyyit.apicontractadapterjava.resource.Resource;
import org.springframework.stereotype.Component;

@Component
public class ContractRegistry {

    private final AtomicReference<ContractSnapshot> current =
            new AtomicReference<>(new ContractSnapshot(Map.of(), Map.of()));
    private volatile Map<String, Object> rawConfig = Map.of();

    public ContractSnapshot snapshot() {
        return current.get();
    }

    public Map<String, Object> rawConfig() {
        return rawConfig;
    }

    public synchronized void reload(Map<String, Object> rawConfig) {
        ContractConfigParser.ParseResult parsed = ContractConfigParser.parse(rawConfig);
        List<RuleError> errors = new ArrayList<>(parsed.errors());
        errors.addAll(ContractValidator.validate(parsed.snapshot()));
        if (!errors.isEmpty()) {
            throw new RuleValidationException(errors);
        }
        this.current.set(parsed.snapshot());
        this.rawConfig = Resource.deepCopy(rawConfig);
    }

    public Optional<ContractRuleSet> contractForClient(String clientId) {
        return snapshot().contractForClient(clientId);
    }
}
