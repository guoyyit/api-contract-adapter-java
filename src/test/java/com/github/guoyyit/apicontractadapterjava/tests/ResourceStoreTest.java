package com.github.guoyyit.apicontractadapterjava.tests;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.guoyyit.apicontractadapterjava.resource.Resource;
import com.github.guoyyit.apicontractadapterjava.resource.ResourceStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceStoreTest {

    @Test
    void saveIncrementsVersionAndReturnsImmutableSnapshot() {
        ResourceStore store = new ResourceStore();
        Resource first = store.save("r1", Map.of("name", "a"));
        Resource second = store.save("r1", Map.of("name", "b"));
        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());
        assertEquals(Map.of("name", "b"), store.get("r1").orElseThrow().getAttributes());
        assertThrows(UnsupportedOperationException.class,
                () -> store.get("r1").orElseThrow().getAttributes().put("x", 1));
    }

    @Test
    void concurrentWritesNeverLoseUpdatesAndReadsStayConsistent() throws Exception {
        ResourceStore store = new ResourceStore();
        int writers = 4;
        int rounds = 100;
        ExecutorService executor = Executors.newFixedThreadPool(writers * 2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> failures = new ConcurrentLinkedQueue<>();

        Runnable writer = () -> {
            await(start);
            for (int i = 0; i < rounds; i++) {
                store.save("shared", Map.of("round", i, "marker", "v" + i));
            }
        };
        Runnable reader = () -> {
            await(start);
            for (int i = 0; i < rounds * 2; i++) {
                Optional<Resource> snapshot = store.get("shared");
                if (snapshot.isPresent()) {
                    Map<String, Object> attributes = snapshot.get().getAttributes();
                    Object round = attributes.get("round");
                    Object marker = attributes.get("marker");
                    if (round == null || marker == null || !marker.equals("v" + round)) {
                        failures.add(new AssertionError("torn read: " + attributes));
                    }
                }
            }
        };

        Future<?>[] futures = new Future<?>[writers * 2];
        for (int i = 0; i < writers; i++) {
            futures[i] = executor.submit(writer);
            futures[writers + i] = executor.submit(reader);
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertTrue(failures.isEmpty(), () -> failures.toString());
        assertEquals((long) writers * rounds, store.get("shared").orElseThrow().getVersion());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
