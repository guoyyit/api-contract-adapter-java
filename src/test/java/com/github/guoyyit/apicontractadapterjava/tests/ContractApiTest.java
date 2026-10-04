package com.github.guoyyit.apicontractadapterjava.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContractApiTest {

    private static final String CONFIG = """
            {
              "contracts": {
                "v1": {
                  "inboundPolicy": "REJECT",
                  "outboundPolicy": "HIDE",
                  "rules": [
                    {"source": "userName", "target": "name"},
                    {"source": "age", "target": "age", "conversion": "TO_INTEGER"}
                  ]
                },
                "v2": {
                  "inboundPolicy": "REJECT",
                  "outboundPolicy": "HIDE",
                  "rules": [
                    {"source": "fullName", "target": "name"}
                  ]
                }
              },
              "bindings": {"client-a": "v1", "client-b": "v2"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void loadContracts() throws Exception {
        mockMvc.perform(put("/admin/contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.contracts").value(2));
    }

    @Test
    void twoClientsSeeSameResourceThroughTheirOwnContracts() throws Exception {
        mockMvc.perform(put("/api/clients/client-a/resources/r1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"alice\",\"age\":\"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("alice"))
                .andExpect(jsonPath("$.age").value(30));

        mockMvc.perform(get("/api/clients/client-a/resources/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("alice"))
                .andExpect(jsonPath("$.age").value(30));

        mockMvc.perform(get("/api/clients/client-b/resources/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("alice"))
                .andExpect(jsonPath("$.age").doesNotExist())
                .andExpect(jsonPath("$.userName").doesNotExist());
    }

    @Test
    void undeclaredInboundFieldYieldsStructured422() throws Exception {
        mockMvc.perform(put("/api/clients/client-a/resources/r2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"x\",\"age\":1,\"unknown\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("unknown"))
                .andExpect(jsonPath("$.errors[0].code").value("UNDECLARED_FIELD"));
    }

    @Test
    void conversionFailureYieldsStructured422WithFieldPath() throws Exception {
        mockMvc.perform(put("/api/clients/client-a/resources/r3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"x\",\"age\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("age"))
                .andExpect(jsonPath("$.errors[0].code").value("CONVERSION_FAILED"));
    }

    @Test
    void invalidRuleReloadIsRejectedAndOldRulesStayActive() throws Exception {
        String invalid = """
                {
                  "contracts": {
                    "broken": {
                      "rules": [
                        {"source": "a", "target": "x", "conversion": "NOPE"},
                        {"source": "a", "target": "y"}
                      ]
                    }
                  },
                  "bindings": {"client-a": "missing"}
                }
                """;
        mockMvc.perform(put("/admin/contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/clients/client-a/resources/r1"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownClientIsRejected() throws Exception {
        mockMvc.perform(get("/api/clients/nobody/resources/r1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
