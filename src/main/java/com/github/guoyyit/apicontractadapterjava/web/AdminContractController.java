package com.github.guoyyit.apicontractadapterjava.web;

import java.util.Map;

import com.github.guoyyit.apicontractadapterjava.contract.ContractRegistry;
import com.github.guoyyit.apicontractadapterjava.contract.ContractSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/contracts")
public class AdminContractController {

    private final ContractRegistry registry;

    public AdminContractController(ContractRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> current() {
        return ResponseEntity.ok(registry.rawConfig());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> reload(@RequestBody Map<String, Object> rawConfig) {
        registry.reload(rawConfig);
        ContractSnapshot snapshot = registry.snapshot();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "contracts", snapshot.getContracts().size(),
                "bindings", snapshot.getClientBindings().size()));
    }
}
