package com.textellent.mcp.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class McpToolCallRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    public McpToolCallRequest() {
    }

    public McpToolCallRequest(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
