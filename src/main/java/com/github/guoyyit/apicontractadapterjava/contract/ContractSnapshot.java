package com.github.guoyyit.apicontractadapterjava.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ContractSnapshot {

    private final Map<String, ContractRuleSet> contracts;
    private final Map<String, String> clientBindings;

    public ContractSnapshot(Map<String, ContractRuleSet> contracts, Map<String, String> clientBindings) {
        this.contracts = Collections.unmodifiableMap(new LinkedHashMap<>(contracts));
        this.clientBindings = Collections.unmodifiableMap(new LinkedHashMap<>(clientBindings));
    }

    public Map<String, ContractRuleSet> getContracts() {
        return contracts;
    }

    public Map<String, String> getClientBindings() {
        return clientBindings;
    }

    public Optional<ContractRuleSet> contractForClient(String clientId) {
        String contractId = clientBindings.get(clientId);
        if (contractId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(contracts.get(contractId));
    }
}
