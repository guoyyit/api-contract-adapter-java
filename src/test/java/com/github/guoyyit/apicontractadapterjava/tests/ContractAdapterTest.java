package com.github.guoyyit.apicontractadapterjava.tests;

import com.github.guoyyit.apicontractadapterjava.contract.engine.ContractAdapter;
import com.github.guoyyit.apicontractadapterjava.contract.engine.TypeConverter;
import com.github.guoyyit.apicontractadapterjava.contract.error.ContractConversionException;
import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.model.UndeclaredFieldPolicy;
import com.github.guoyyit.apicontractadapterjava.contract.registry.ContractRuleRegistry;
import com.github.guoyyit.apicontractadapterjava.contract.registry.RuleSetValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractAdapterTest {

    private static final String CONTRACT = "test-contract";

    private ContractRuleRegistry registry;
    private ContractAdapter adapter;

    @BeforeEach
    void setUp() {
        TypeConverter converter = new TypeConverter();
        registry = new ContractRuleRegistry(new RuleSetValidator(converter));
        adapter = new ContractAdapter(registry, converter);
    }

    private void bind(ContractRuleSet ruleSet) {
        registry.reload(ruleSet);
    }

    private ContractRuleSet ruleSet(UndeclaredFieldPolicy policy, FieldRule... rules) {
        return new ContractRuleSet(CONTRACT, 1L, List.of(rules), policy);
    }

    @Test
    void nestedMappingRenamesAndMigratesPaths() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("user_name", "profile.name", null, null, false),
                new FieldRule("address.zip", "location.postal_code", null, null, false)));

        Map<String, Object> internal = adapter.toInternal(CONTRACT, Map.of(
                "user_name", "ann",
                "address", Map.of("zip", "100000")));

        assertEquals(Map.of(
                "profile", Map.of("name", "ann"),
                "location", Map.of("postal_code", "100000")), internal);

        Map<String, Object> clientView = adapter.toClientView(CONTRACT, internal);
        assertEquals(Map.of(
                "user_name", "ann",
                "address", Map.of("zip", "100000")), clientView);
    }

    @Test
    void collectionElementsAreMappedRecursivelyPreservingOrder() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("items[].price", "line_items[].unit_price", "double", null, false),
                new FieldRule("items[].sku", "line_items[].sku", null, null, false)));

        Map<String, Object> internal = adapter.toInternal(CONTRACT, Map.of(
                "items", List.of(
                        Map.of("price", "1.5", "sku", "a"),
                        Map.of("price", "2.0", "sku", "b"),
                        Map.of("price", "3.25", "sku", "c"))));

        Object lineItems = internal.get("line_items");
        assertTrue(lineItems instanceof List<?>);
        assertEquals(List.of(
                Map.of("unit_price", 1.5, "sku", "a"),
                Map.of("unit_price", 2.0, "sku", "b"),
                Map.of("unit_price", 3.25, "sku", "c")), lineItems);

        Map<String, Object> clientView = adapter.toClientView(CONTRACT, internal);
        assertEquals(List.of(
                Map.of("price", 1.5, "sku", "a"),
                Map.of("price", 2.0, "sku", "b"),
                Map.of("price", 3.25, "sku", "c")), clientView.get("items"));
    }

    @Test
    void typeConversionFailureYieldsStructuredErrorsWithPathAndReason() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("age", "age", "integer", null, false),
                new FieldRule("score", "score", "double", null, false)));

        ContractConversionException ex = assertThrows(ContractConversionException.class,
                () -> adapter.toInternal(CONTRACT, Map.of("age", "abc", "score", "not-a-number")));

        assertEquals(2, ex.getErrors().size());
        assertEquals("age", ex.getErrors().get(0).path());
        assertTrue(ex.getErrors().get(0).reason().contains("cannot convert"));
        assertEquals("score", ex.getErrors().get(1).path());
        assertTrue(ex.getErrors().get(1).reason().contains("cannot convert"));
    }

    @Test
    void unmappableFieldStructureYieldsStructuredError() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("profile.name", "name", null, null, false)));

        ContractConversionException ex = assertThrows(ContractConversionException.class,
                () -> adapter.toInternal(CONTRACT, Map.of("profile", "not-an-object")));

        assertEquals(1, ex.getErrors().size());
        assertEquals("profile.name", ex.getErrors().get(0).path());
        assertTrue(ex.getErrors().get(0).reason().contains("expected an object"));
    }

    @Test
    void missingFieldsUseDefaultsAndRequiredMissingFails() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("optional", "optional", null, null, false),
                new FieldRule("with_default", "with_default", null, "dflt", false),
                new FieldRule("must_have", "must_have", null, null, true)));

        ContractConversionException ex = assertThrows(ContractConversionException.class,
                () -> adapter.toInternal(CONTRACT, Map.of()));

        assertEquals(1, ex.getErrors().size());
        assertEquals("must_have", ex.getErrors().get(0).path());
        assertTrue(ex.getErrors().get(0).reason().contains("required"));
    }

    @Test
    void missingAndNullAreHandledDifferently() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("absent", "absent", null, null, false),
                new FieldRule("with_default", "with_default", null, "dflt", false),
                new FieldRule("explicit_null", "explicit_null", null, null, false)));

        Map<String, Object> internal = adapter.toInternal(CONTRACT, mapWithNull("explicit_null"));

        assertFalse(internal.containsKey("absent"), "missing field without default must be skipped");
        assertEquals("dflt", internal.get("with_default"));
        assertTrue(internal.containsKey("explicit_null"), "explicit null must be preserved");
        assertEquals(null, internal.get("explicit_null"));
    }

    @Test
    void undeclaredFieldPolicyControlsExposure() {
        FieldRule declared = new FieldRule("name", "name", null, null, false);
        Map<String, Object> internal = Map.of("name", "ann", "secret", "hidden");

        bind(ruleSet(UndeclaredFieldPolicy.OMIT, declared));
        assertEquals(Map.of("name", "ann"), adapter.toClientView(CONTRACT, internal));

        bind(ruleSet(UndeclaredFieldPolicy.EXPOSE, declared));
        assertEquals(Map.of("name", "ann", "secret", "hidden"),
                adapter.toClientView(CONTRACT, internal));

        bind(ruleSet(UndeclaredFieldPolicy.FAIL, declared));
        ContractConversionException ex = assertThrows(ContractConversionException.class,
                () -> adapter.toClientView(CONTRACT, internal));
        assertEquals("secret", ex.getErrors().get(0).path());
    }

    @Test
    void sameDataConvertsIdenticallyWithinRequest() {
        bind(ruleSet(UndeclaredFieldPolicy.EXPOSE,
                new FieldRule("n", "n", "integer", null, false)));
        Map<String, Object> internal = Map.of("n", 41, "extra", "e");

        Map<String, Object> first = adapter.toClientView(CONTRACT, internal);
        Map<String, Object> second = adapter.toClientView(CONTRACT, internal);
        assertEquals(first, second);

        Map<String, Object> clientView = Map.of("n", "42");
        assertEquals(adapter.toInternal(CONTRACT, clientView),
                adapter.toInternal(CONTRACT, clientView));
    }

    @Test
    void undeclaredInboundFieldsAreIgnored() {
        bind(ruleSet(UndeclaredFieldPolicy.OMIT,
                new FieldRule("name", "name", null, null, false)));

        Map<String, Object> internal = adapter.toInternal(CONTRACT,
                Map.of("name", "ann", "future_field", "ignored"));

        assertEquals(Map.of("name", "ann"), internal);
    }

    private static Map<String, Object> mapWithNull(String key) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put(key, null);
        return map;
    }
}
