package com.mailengine;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailengine.worker.SendLoopScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mail-engine.runtime.skip-domain-verification=true")
class MailEngineApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SendLoopScheduler sendLoopScheduler;

    @Test
    void healthEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMode").value("LOCAL_OUTBOX"));
    }

    @Test
    void tenantAndDomainFlowWorks() throws Exception {
        String tenantJson = """
                {"name":"Anybody"}
                """;

        String tenantResponse = mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Anybody"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tenantId = extractField(tenantResponse, "id");

        // Create API key (no auth needed for first key) and use it for all subsequent calls
        String apiKeyResponse = mockMvc.perform(post("/api/tenants/" + tenantId + "/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test-key"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String apiKey = extractField(apiKeyResponse, "rawKey");

        String domainResponse = mockMvc.perform(post("/api/tenants/" + tenantId + "/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content("""
                                {"domainName":"mail.anybody.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domainName").value("mail.anybody.com"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.dkimSelector").value("me1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String domainId = extractField(domainResponse, "id");

        mockMvc.perform(get("/api/tenants/" + tenantId + "/domains/" + domainId + "/dns-records")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].name").value(hasItem("_mailengine.mail.anybody.com")))
                .andExpect(jsonPath("$[*].name").value(hasItem("me1._domainkey.mail.anybody.com")))
                .andExpect(jsonPath("$[*].name").value(hasItem("_dmarc.mail.anybody.com")))
                .andExpect(jsonPath("$[*].value").value(hasItem("v=spf1 mx ~all")));

        mockMvc.perform(post("/api/tenants/" + tenantId + "/domains/" + domainId + "/verify")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        String ipPoolResponse = mockMvc.perform(post("/api/tenants/" + tenantId + "/ip-pools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content("""
                                {"name":"marketing-primary","trafficType":"marketing"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trafficType").value("MARKETING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String ipPoolId = extractField(ipPoolResponse, "id");

        mockMvc.perform(post("/api/tenants/" + tenantId + "/ip-pools/" + ipPoolId + "/ips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content("""
                                {
                                  "publicIpAddress":"203.0.113.10",
                                  "elasticAllocationId":"eipalloc-1234567890abcdef0",
                                  "reverseDnsName":"mail.anybody.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/tenants/" + tenantId + "/suppressions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content("""
                                {
                                  "email":"blocked@example.com",
                                  "reason":"unsubscribe"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("UNSUBSCRIBE"));

        String campaignResponse = mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", apiKey)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "domainId":"%s",
                                  "name":"Welcome Blast",
                                  "subject":"Hello from Mail Engine",
                                  "body":"This is a local test send.",
                                  "recipientEmails":["test@example.com", "blocked@example.com"]
                                }
                                """.formatted(tenantId, domainId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Hello from Mail Engine"))
                .andExpect(jsonPath("$.recipientCount").value(2))
                .andExpect(jsonPath("$.messageJobCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String campaignId = extractField(campaignResponse, "id");

        // Trigger send loop manually — in tests the 5s scheduler hasn't fired yet
        sendLoopScheduler.poll();

        mockMvc.perform(get("/api/campaigns/" + campaignId + "/jobs")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status").value(hasItem("SENT")))
                .andExpect(jsonPath("$[*].status").value(hasItem("SUPPRESSED")));

        mockMvc.perform(get("/api/campaigns/outbox")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryStatus").value("LOCAL_CAPTURED"))
                .andExpect(jsonPath("$[0].outboundIpAddress").value("203.0.113.10"))
                .andExpect(jsonPath("$[0].recipientEmail").value("test@example.com"));
    }

    private String extractField(String json, String field) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode value = root.get(field);
            if (value == null || value.isNull()) {
                throw new IllegalStateException("Missing field: " + field);
            }
            return value.asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse json", ex);
        }
    }
}
