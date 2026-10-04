package com.github.guoyyit.apicontractadapterjava.tests;

import com.github.guoyyit.apicontractadapterjava.contract.model.ContractRuleSet;
import com.github.guoyyit.apicontractadapterjava.contract.model.FieldRule;
import com.github.guoyyit.apicontractadapterjava.contract.model.UndeclaredFieldPolicy;
import com.github.guoyyit.apicontractadapterjava.contract.registry.ContractRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContractRuleRegistry ruleRegistry;

    @BeforeEach
    void bindContracts() {
        ruleRegistry.reload(new ContractRuleSet("web-v1", 1L, List.of(
                new FieldRule("display_name", "profile.name", null, null, true),
                new FieldRule("age", "metrics.age", "integer", null, false)),
                UndeclaredFieldPolicy.OMIT));
        ruleRegistry.reload(new ContractRuleSet("web-v2", 1L, List.of(
                new FieldRule("name", "profile.name", null, null, true),
                new FieldRule("years", "metrics.age", "integer", 0, false)),
                UndeclaredFieldPolicy.OMIT));
    }

    @Test
    void putAndGetRoundTripThroughContract() throws Exception {
        mockMvc.perform(put("/api/resources/r1?contract=web-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"display_name\":\"ann\",\"age\":\"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("ann"))
                .andExpect(jsonPath("$.age").value(30));

        mockMvc.perform(get("/api/resources/r1?contract=web-v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ann"))
                .andExpect(jsonPath("$.years").value(30));
    }

    @Test
    void conversionFailureReturnsStructuredErrors() throws Exception {
        mockMvc.perform(put("/api/resources/r2?contract=web-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":\"not-a-number\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("display_name"))
                .andExpect(jsonPath("$.errors[1].path").value("age"))
                .andExpect(jsonPath("$.errors[1].reason").value(
                        org.hamcrest.Matchers.containsString("cannot convert")));
    }

    @Test
    void invalidRuleReloadIsRejectedAndActiveRulesSurvive() throws Exception {
        mockMvc.perform(put("/api/contracts/web-v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"undeclaredFieldPolicy\":\"OMIT\",\"rules\":["
                                + "{\"sourcePath\":\"a\",\"targetPath\":\"a\",\"type\":\"nope\","
                                + "\"defaultValue\":null,\"required\":false},"
                                + "{\"sourcePath\":\"a\",\"targetPath\":\"b\",\"type\":null,"
                                + "\"defaultValue\":null,\"required\":false}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        mockMvc.perform(get("/api/contracts/web-v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unknownContractIsRejected() throws Exception {
        mockMvc.perform(get("/api/resources/r1?contract=nope"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].reason").value(
                        org.hamcrest.Matchers.containsString("no rule set bound")));
    }
}
