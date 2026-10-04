package com.github.guoyyit.apicontractadapterjava.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.guoyyit.apicontractadapterjava.contract.ContractRegistry;
import com.github.guoyyit.apicontractadapterjava.contract.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.ContractSnapshot;
import com.github.guoyyit.apicontractadapterjava.contract.RuleValidationException;
import com.github.guoyyit.apicontractadapterjava.transform.ContractTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRegistryTest {

    private static Map<String, Object> configV1() {
        return Map.of(
                "contracts", Map.of("c1", Map.of(
                        "inboundPolicy", "IGNORE",
                        "rules", List.of(Map.of("source", "name", "target", "name")))),
                "bindings", Map.of("client-x", "c1"));
    }

    private static Map<String, Object> configV2() {
        return Map.of(
                "contracts", Map.of("c2", Map.of(
                        "inboundPolicy", "IGNORE",
                        "rules", List.of(Map.of("source", "name", "target", "renamed")))),
                "bindings", Map.of("client-x", "c2"));
    }

    @Test
    void reloadAppliesNewRulesAtomically() {
        ContractRegistry registry = new ContractRegistry();
        registry.reload(configV1());
        assertTrue(registry.contractForClient("client-x").isPresent());
        assertEquals("c1", registry.snapshot().getClientBindings().get("client-x"));

        registry.reload(configV2());
        ContractRuleSet ruleSet = registry.contractForClient("client-x").orElseThrow();
        assertEquals("renamed", ruleSet.getRules().get(0).getTarget());
    }

    @Test
    void invalidConfigIsRejectedWholesaleWithFullErrorListAndKeepsOldRules() {
        ContractRegistry registry = new ContractRegistry();
        registry.reload(configV1());
        ContractSnapshot before = registry.snapshot();

        Map<String, Object> invalid = Map.of(
                "contracts", Map.of("bad", Map.of(
                        "inboundPolicy", "EXPOSE",
                        "rules", List.of(
                                Map.of("source", "a", "target", "x", "conversion", "NOPE"),
                                Map.of("source", "a", "target", "y"),
                                Map.of("target", "z")))),
                "bindings", Map.of("client-x", "missing-contract"));

        RuleValidationException ex = assertThrows(RuleValidationException.class,
                () -> registry.reload(invalid));
        assertTrue(ex.getErrors().size() >= 4,
                "expected at least 4 collected errors but got " + ex.getErrors());

        assertTrue(registry.snapshot() == before, "old snapshot must stay in effect");
        assertEquals("c1", registry.snapshot().getClientBindings().get("client-x"));
    }

    @Test
    void inFlightRequestsSeeOneCompleteRuleSetDuringConcurrentReloads() throws Exception {
        ContractRegistry registry = new ContractRegistry();
        registry.reload(configV1());
        ContractTransformer transformer = new ContractTransformer();
        Map<String, Object> document = Map.of("name", "alice", "extra", 1);
        Map<String, Object> expectedV1 = Map.of("name", "alice");
        Map<String, Object> expectedV2 = Map.of("renamed", "alice");

        int rounds = 200;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> failures = new ConcurrentLinkedQueue<>();

        Runnable reloader = () -> {
            await(start);
            for (int i = 0; i < rounds; i++) {
                registry.reload(i % 2 == 0 ? configV2() : configV1());
            }
        };
        Runnable reader = () -> {
            await(start);
            for (int i = 0; i < rounds; i++) {
                ContractSnapshot snapshot = registry.snapshot();
                ContractRuleSet ruleSet = snapshot.contractForClient("client-x").orElseThrow();
                Map<String, Object> result = transformer.inbound(ruleSet, document);
                if (!result.equals(expectedV1) && !result.equals(expectedV2)) {
                    failures.add(new AssertionError("mixed rule transformation: " + result));
                }
            }
        };

        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(reloader));
        futures.add(executor.submit(reader));
        futures.add(executor.submit(reader));
        futures.add(executor.submit(reader));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdownNow();
        assertTrue(failures.isEmpty(), () -> failures.toString());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
