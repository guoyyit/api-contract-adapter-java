package com.github.guoyyit.apicontractadapterjava.tests;

import com.github.guoyyit.apicontractadapterjava.contract.engine.ContractAdapter;
import com.github.guoyyit.apicontractadapterjava.contract.engine.TypeConverter;
import com.github.guoyyit.apicontractadapterjava.contract.error.RuleValidationException;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.model.UndeclaredFieldPolicy;
import com.github.guoyyit.apicontractadapterjava.contract.registry.ContractRuleRegistry;
import com.github.guoyyit.apicontractadapterjava.contract.registry.RuleSetValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRuleRegistryTest {

    private static final String CONTRACT = "hot-contract";

    private ContractRuleRegistry registry;
    private ContractAdapter adapter;

    @BeforeEach
    void setUp() {
        TypeConverter converter = new TypeConverter();
        registry = new ContractRuleRegistry(new RuleSetValidator(converter));
        adapter = new ContractAdapter(registry, converter);
    }

    @Test
    void invalidRuleSetIsRejectedWithFullErrorList() {
        ContractRuleSet invalid = new ContractRuleSet(CONTRACT, 2L, List.of(
                new FieldRule("a", "a", "unsupported-type", null, false),
                new FieldRule("a", "b", null, null, false),
                new FieldRule("c", "", null, null, false),
                new FieldRule("d", "x..y", null, null, false)),
                UndeclaredFieldPolicy.OMIT);

        RuleValidationException ex = assertThrows(RuleValidationException.class,
                () -> registry.reload(invalid));

        assertEquals(4, ex.getErrors().size());
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("unsupported type")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("duplicate source path")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("must not be blank")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("empty segment")));
        assertTrue(registry.currentRules(CONTRACT).isEmpty(),
                "rejected rule set must not become active");
    }

    @Test
    void rejectedReloadKeepsPreviousRulesActive() {
        ContractRuleSet v1 = new ContractRuleSet(CONTRACT, 1L,
                List.of(new FieldRule("name", "name", null, null, false)),
                UndeclaredFieldPolicy.OMIT);
        registry.reload(v1);

        ContractRuleSet invalidV2 = new ContractRuleSet(CONTRACT, 2L,
                List.of(new FieldRule("name", "name", "nope", null, false)),
                UndeclaredFieldPolicy.OMIT);
        assertThrows(RuleValidationException.class, () -> registry.reload(invalidV2));

        ContractRuleSet active = registry.currentRules(CONTRACT).orElseThrow();
        assertEquals(1L, active.version());
        assertEquals(Map.of("name", "ann"), adapter.toInternal(CONTRACT, Map.of("name", "ann")));
    }

    @Test
    void hotReloadIsAtomicUnderConcurrency() throws InterruptedException {
        ContractRuleSet v1 = new ContractRuleSet(CONTRACT, 1L,
                List.of(new FieldRule("old_name", "name", null, null, false)),
                UndeclaredFieldPolicy.OMIT);
        ContractRuleSet v2 = new ContractRuleSet(CONTRACT, 2L,
                List.of(new FieldRule("new_name", "name", null, null, false)),
                UndeclaredFieldPolicy.OMIT);
        registry.reload(v1);

        Map<String, Object> request = Map.of("old_name", "x", "new_name", "y");
        Set<Map<String, Object>> allowed = Set.of(Map.of("name", "x"), Map.of("name", "y"));

        int readers = 4;
        int iterations = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Map<String, Object>> observed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < readers; i++) {
            pool.submit(() -> {
                await(start);
                for (int j = 0; j < iterations; j++) {
                    try {
                        observed.add(adapter.toInternal(CONTRACT, request));
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                }
            });
        }
        pool.submit(() -> {
            await(start);
            for (int j = 0; j < iterations; j++) {
                registry.reload(j % 2 == 0 ? v1 : v2);
            }
        });

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertTrue(failures.isEmpty(), "no conversion may fail during hot reload: " + failures);
        assertEquals(readers * iterations, observed.size());
        for (Map<String, Object> result : observed) {
            assertTrue(allowed.contains(result),
                    "result must come entirely from v1 or v2, got: " + result);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
