package com.textellent.mcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolDefinition {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("inputSchema")
    private Map<String, Object> inputSchema;

    @JsonProperty("outputSchema")
    private Map<String, Object> outputSchema;

    @JsonProperty("readOnly")
    private Boolean readOnly;

    @JsonProperty("destructive")
    private Boolean destructive;

    @JsonProperty("requiredScope")
    private String requiredScope;

    public McpToolDefinition() {
    }

    public McpToolDefinition(String name, String description, Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
    }

    public McpToolDefinition(String name, String description, Map<String, Object> inputSchema,
                            Map<String, Object> outputSchema, Boolean readOnly, Boolean destructive, String requiredScope) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.readOnly = readOnly;
        this.destructive = destructive;
        this.requiredScope = requiredScope;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Boolean getDestructive() {
        return destructive;
    }

    public void setDestructive(Boolean destructive) {
        this.destructive = destructive;
    }

    public String getRequiredScope() {
        return requiredScope;
    }

    public void setRequiredScope(String requiredScope) {
        this.requiredScope = requiredScope;
    }
}
