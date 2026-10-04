package com.github.guoyyit.apicontractadapterjava.web;

import com.github.guoyyit.apicontractadapterjava.contract.error.RuleValidationException;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.model.UndeclaredFieldPolicy;
import com.github.guoyyit.apicontractadapterjava.contract.registry.ContractRuleRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 规则管理端点：规则集整体热更新，原子生效；非法规则整体拒绝并返回完整错误清单。
 */
@RestController
@RequestMapping("/api/contracts")
public class RuleAdminController {

    private final ContractRuleRegistry ruleRegistry;

    public RuleAdminController(ContractRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * 规则集热更新请求体。
     *
     * @param version               规则版本号
     * @param undeclaredFieldPolicy 未声明字段暴露策略（EXPOSE/OMIT/FAIL）
     * @param rules                 字段映射规则列表
     */
    public record RuleSetRequest(long version, String undeclaredFieldPolicy, List<FieldRule> rules) {
    }

    @PutMapping("/{contractId}/rules")
    public ResponseEntity<Map<String, Object>> reload(@PathVariable String contractId,
                                                      @RequestBody RuleSetRequest request) {
        ContractRuleSet candidate = new ContractRuleSet(
                contractId, request.version(), request.rules(), parsePolicy(request));
        ContractRuleSet active = ruleRegistry.reload(candidate);
        return ResponseEntity.ok(Map.of(
                "contractId", active.contractId(),
                "version", active.version(),
                "ruleCount", active.rules().size()));
    }

    @GetMapping("/{contractId}/rules")
    public ResponseEntity<Map<String, Object>> current(@PathVariable String contractId) {
        return ruleRegistry.currentRules(contractId)
                .map(active -> ResponseEntity.ok(Map.of(
                        "contractId", active.contractId(),
                        "version", active.version(),
                        "undeclaredFieldPolicy", active.undeclaredFieldPolicy().name(),
                        "rules", active.rules())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private UndeclaredFieldPolicy parsePolicy(RuleSetRequest request) {
        if (request.undeclaredFieldPolicy() == null) {
            return null;
        }
        try {
            return UndeclaredFieldPolicy.valueOf(request.undeclaredFieldPolicy());
        } catch (IllegalArgumentException e) {
            throw new RuleValidationException(List.of(
                    "undeclaredFieldPolicy: unsupported value '" + request.undeclaredFieldPolicy() + "'"));
        }
    }
}
