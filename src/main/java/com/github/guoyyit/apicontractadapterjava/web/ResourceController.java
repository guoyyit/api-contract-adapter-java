package com.github.guoyyit.apicontractadapterjava.web;

import com.github.guoyyit.apicontractadapterjava.contract.engine.ContractAdapter;
import com.github.guoyyit.apicontractadapterjava.store.ResourceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对外接口：按客户端绑定契约双向转换请求与响应。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceStore resourceStore;
    private final ContractAdapter contractAdapter;

    public ResourceController(ResourceStore resourceStore, ContractAdapter contractAdapter) {
        this.resourceStore = resourceStore;
        this.contractAdapter = contractAdapter;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id,
                                                   @RequestParam String contract) {
        return resourceStore.snapshot(id)
                .map(internal -> ResponseEntity.ok(contractAdapter.toClientView(contract, internal)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> put(@PathVariable String id,
                                                    @RequestParam String contract,
                                                    @RequestBody Map<String, Object> clientView) {
        Map<String, Object> internal = contractAdapter.toInternal(contract, clientView);
        resourceStore.put(id, internal);
        return ResponseEntity.ok(contractAdapter.toClientView(contract, internal));
    }
}
