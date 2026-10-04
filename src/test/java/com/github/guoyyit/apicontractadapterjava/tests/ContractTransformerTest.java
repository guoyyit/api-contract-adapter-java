package com.github.guoyyit.apicontractadapterjava.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.guoyyit.apicontractadapterjava.contract.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.TypeConversion;
import com.github.guoyyit.apicontractadapterjava.contract.UndeclaredFieldPolicy;
import com.github.guoyyit.apicontractadapterjava.transform.ContractTransformer;
import com.github.guoyyit.apicontractadapterjava.transform.FieldError;
import com.github.guoyyit.apicontractadapterjava.transform.TransformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractTransformerTest {

    private final ContractTransformer transformer = new ContractTransformer();

    private static FieldRule rule(String source, String target) {
        FieldRule rule = new FieldRule();
        rule.setSource(source);
        rule.setTarget(target);
        return rule;
    }

    private static FieldRule rule(String source, String target, TypeConversion conversion) {
        FieldRule rule = rule(source, target);
        rule.setConversion(conversion);
        return rule;
    }

    private static ContractRuleSet ruleSet(List<FieldRule> rules) {
        ContractRuleSet ruleSet = new ContractRuleSet();
        ruleSet.setId("test");
        ruleSet.setRules(rules);
        ruleSet.setInboundPolicy(UndeclaredFieldPolicy.IGNORE);
        return ruleSet;
    }

    @Test
    void mapsNestedObjectsRecursivelyInBothDirections() {
        FieldRule address = rule("address", "location");
        address.setChildren(List.of(rule("city", "town"), rule("zip", "postalCode")));
        ContractRuleSet ruleSet = ruleSet(List.of(address, rule("name", "displayName")));

        Map<String, Object> clientDoc = new LinkedHashMap<>();
        clientDoc.put("name", "alice");
        clientDoc.put("address", Map.of("city", "Beijing", "zip", "100000"));

        Map<String, Object> internal = transformer.inbound(ruleSet, clientDoc);
        assertEquals(Map.of("displayName", "alice",
                "location", Map.of("town", "Beijing", "postalCode", "100000")), internal);

        Map<String, Object> back = transformer.outbound(ruleSet, internal);
        assertEquals(clientDoc, back);
    }

    @Test
    void mapsCollectionElementsPreservingOrderWithoutDuplicates() {
        FieldRule items = rule("items", "lines");
        items.setChildren(List.of(rule("name", "title")));
        ContractRuleSet ruleSet = ruleSet(List.of(items));

        List<Object> elements = new ArrayList<>();
        elements.add(Map.of("name", "first"));
        elements.add(null);
        elements.add(Map.of("name", "third"));
        Map<String, Object> clientDoc = Map.of("items", elements);

        Map<String, Object> internal = transformer.inbound(ruleSet, clientDoc);
        Object lines = internal.get("lines");
        assertTrue(lines instanceof List<?>);
        assertEquals(Arrays.asList(Map.of("title", "first"), null, Map.of("title", "third")), lines);

        Map<String, Object> back = transformer.outbound(ruleSet, internal);
        assertEquals(elements, back.get("items"));
    }

    @Test
    void distinguishesMissingFromNullAndAppliesDefaults() {
        FieldRule withDefault = rule("score", "score");
        withDefault.setHasDefault(true);
        withDefault.setDefaultValue(0);
        FieldRule optional = rule("nickname", "nickname");
        optional.setRequired(false);
        ContractRuleSet ruleSet = ruleSet(List.of(rule("name", "name"), withDefault, optional));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("name", null);
        Map<String, Object> internal = transformer.inbound(ruleSet, doc);

        assertTrue(internal.containsKey("name"));
        assertNull(internal.get("name"));
        assertEquals(0, internal.get("score"));
        assertTrue(!internal.containsKey("nickname"));
    }

    @Test
    void reportsStructuredErrorForMissingRequiredField() {
        ContractRuleSet ruleSet = ruleSet(List.of(rule("name", "name")));
        TransformException ex = assertThrows(TransformException.class,
                () -> transformer.inbound(ruleSet, Map.of()));
        assertEquals(1, ex.getErrors().size());
        FieldError error = ex.getErrors().get(0);
        assertEquals("name", error.path());
        assertEquals(FieldError.FIELD_MISSING, error.code());
    }

    @Test
    void reportsStructuredErrorWithPathForConversionFailure() {
        ContractRuleSet ruleSet = ruleSet(List.of(rule("age", "age", TypeConversion.TO_INTEGER)));
        TransformException ex = assertThrows(TransformException.class,
                () -> transformer.inbound(ruleSet, Map.of("age", "not-a-number")));
        assertEquals(1, ex.getErrors().size());
        FieldError error = ex.getErrors().get(0);
        assertEquals("age", error.path());
        assertEquals(FieldError.CONVERSION_FAILED, error.code());
        assertTrue(error.message().contains("not-a-number"));
    }

    @Test
    void rejectsUndeclaredInboundFieldsByDefault() {
        ContractRuleSet ruleSet = ruleSet(List.of(rule("name", "name")));
        ruleSet.setInboundPolicy(UndeclaredFieldPolicy.REJECT);
        TransformException ex = assertThrows(TransformException.class,
                () -> transformer.inbound(ruleSet, Map.of("name", "a", "extra", 1)));
        assertEquals(1, ex.getErrors().size());
        assertEquals("extra", ex.getErrors().get(0).path());
        assertEquals(FieldError.UNDECLARED_FIELD, ex.getErrors().get(0).code());
    }

    @Test
    void outboundUndeclaredPolicyExposesOrHidesFields() {
        ContractRuleSet exposing = ruleSet(List.of(rule("name", "name")));
        exposing.setOutboundPolicy(UndeclaredFieldPolicy.EXPOSE);
        ContractRuleSet hiding = ruleSet(List.of(rule("name", "name")));
        hiding.setOutboundPolicy(UndeclaredFieldPolicy.HIDE);

        Map<String, Object> internal = Map.of("name", "a", "internalNote", "secret");
        assertEquals(Map.of("name", "a", "internalNote", "secret"),
                transformer.outbound(exposing, internal));
        assertEquals(Map.of("name", "a"), transformer.outbound(hiding, internal));
    }

    @Test
    void transformationIsDeterministicForSameInput() {
        FieldRule address = rule("address", "location");
        address.setChildren(List.of(rule("city", "town")));
        ContractRuleSet ruleSet = ruleSet(List.of(rule("name", "displayName"), address));
        ruleSet.setOutboundPolicy(UndeclaredFieldPolicy.EXPOSE);

        Map<String, Object> internal = new LinkedHashMap<>();
        internal.put("zeta", 1);
        internal.put("displayName", "a");
        internal.put("alpha", 2);
        internal.put("location", Map.of("town", "BJ"));

        Map<String, Object> first = transformer.outbound(ruleSet, internal);
        Map<String, Object> second = transformer.outbound(ruleSet, internal);
        assertEquals(first, second);
        assertEquals(new ArrayList<>(first.keySet()), new ArrayList<>(second.keySet()));
    }
}
