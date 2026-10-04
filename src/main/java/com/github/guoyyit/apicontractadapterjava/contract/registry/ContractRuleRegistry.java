package com.github.guoyyit.apicontractadapterjava.contract.registry;

import com.github.guoyyit.apicontractadapterjava.contract.error.RuleValidationException;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 规则注册表：持有各客户端契约的生效规则集，支持原子热更新。
 *
 * <p>规则集不可变，替换以单个契约粒度原子生效：在途请求持有旧引用则完整使用旧规则，
 * 新请求获取新引用则完整使用新规则，不会出现混合转换。
 */
@Component
public class ContractRuleRegistry {

    private final RuleSetValidator validator;
    private final ConcurrentMap<String, ContractRuleSet> activeRules = new ConcurrentHashMap<>();

    public ContractRuleRegistry(RuleSetValidator validator) {
        this.validator = validator;
    }

    /**
     * 查询当前生效的规则集。
     */
    public Optional<ContractRuleSet> currentRules(String contractId) {
        return Optional.ofNullable(activeRules.get(contractId));
    }

    /**
     * 校验并原子替换指定契约的规则集；非法规则整体拒绝且不影响在途请求。
     */
    public ContractRuleSet reload(ContractRuleSet candidate) {
        List<String> errors = validator.validate(candidate);
        if (!errors.isEmpty()) {
            throw new RuleValidationException(errors);
        }
        activeRules.put(candidate.contractId(), candidate);
        return candidate;
    }
}
