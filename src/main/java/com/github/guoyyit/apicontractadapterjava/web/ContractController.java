package com.github.guoyyit.apicontractadapterjava.web;

import java.util.Map;

import com.github.guoyyit.apicontractadapterjava.contract.ContractRegistry;
import com.github.guoyyit.apicontractadapterjava.contract.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.ContractSnapshot;
import com.github.guoyyit.apicontractadapterjava.contract.UnknownContractException;
import com.github.guoyyit.apicontractadapterjava.resource.Resource;
import com.github.guoyyit.apicontractadapterjava.resource.ResourceStore;
import com.github.guoyyit.apicontractadapterjava.transform.ContractTransformer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/{clientId}/resources")
public class ContractController {

    private final ContractRegistry registry;
    private final ContractTransformer transformer;
    private final ResourceStore store;

    public ContractController(ContractRegistry registry, ContractTransformer transformer, ResourceStore store) {
        this.registry = registry;
        this.transformer = transformer;
        this.store = store;
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String clientId, @PathVariable String resourceId) {
        ContractSnapshot snapshot = registry.snapshot();
        ContractRuleSet ruleSet = snapshot.contractForClient(clientId)
                .orElseThrow(() -> new UnknownContractException(clientId));
        return store.get(resourceId)
                .map(resource -> ResponseEntity.ok(transformer.outbound(ruleSet, resource.getAttributes())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{resourceId}")
    public ResponseEntity<Map<String, Object>> put(@PathVariable String clientId,
                                                   @PathVariable String resourceId,
                                                   @RequestBody Map<String, Object> body) {
        ContractSnapshot snapshot = registry.snapshot();
        ContractRuleSet ruleSet = snapshot.contractForClient(clientId)
                .orElseThrow(() -> new UnknownContractException(clientId));
        Map<String, Object> internal = transformer.inbound(ruleSet, body);
        Resource saved = store.save(resourceId, internal);
        return ResponseEntity.ok(transformer.outbound(ruleSet, saved.getAttributes()));
    }
}
