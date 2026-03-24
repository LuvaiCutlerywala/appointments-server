package com.textellent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.McpRpcRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for security and scope enforcement.
 */
@SpringBootTest(properties = "security.mode=local")
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    public void testVersionEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/version"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("textellent-appointments-mcp-server"));
    }

    @Test
    public void testInitializeMethod() throws Exception {
        McpRpcRequest request = new McpRpcRequest();
        request.setJsonrpc("2.0");
        request.setId(1);
        request.setMethod("initialize");
        request.setParams(new HashMap<>());

        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.result.protocolVersion").exists())
            .andExpect(jsonPath("$.result.serverInfo.name").value("textellent-appointments-mcp-server"));
    }

    @Test
    public void testToolsListMethod() throws Exception {
        McpRpcRequest request = new McpRpcRequest();
        request.setJsonrpc("2.0");
        request.setId(2);
        request.setMethod("tools/list");
        request.setParams(new HashMap<>());

        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.result.tools").isArray())
            .andExpect(jsonPath("$.result.totalCount").isNumber());
    }

    @Test
    public void testInvalidJsonRpcVersion() throws Exception {
        McpRpcRequest request = new McpRpcRequest();
        request.setJsonrpc("1.0");
        request.setId(3);
        request.setMethod("tools/list");
        request.setParams(new HashMap<>());

        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value(-32600))
            .andExpect(jsonPath("$.error.message").value("Invalid JSON-RPC version"));
    }

    @Test
    public void testMethodNotFound() throws Exception {
        McpRpcRequest request = new McpRpcRequest();
        request.setJsonrpc("2.0");
        request.setId(4);
        request.setMethod("invalid/method");
        request.setParams(new HashMap<>());

        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    public void testToolCallWithoutScopeOrAuthCode() throws Exception {
        McpRpcRequest request = new McpRpcRequest();
        request.setJsonrpc("2.0");
        request.setId(5);
        request.setMethod("tools/call");

        Map<String, Object> params = new HashMap<>();
        params.put("name", "appointments_create");
        params.put("arguments", new HashMap<>());
        request.setParams(params);

        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value(-32602))
            .andExpect(jsonPath("$.error.message").value(containsString("dsl_execute_plan")));
    }
}
