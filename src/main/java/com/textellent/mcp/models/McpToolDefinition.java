package com.textellent.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
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

    /**
     * MCP-compliant annotations object containing safety hints.
     * This is what ChatGPT Apps uses to determine if a tool requires confirmation.
     */
    @JsonProperty("annotations")
    private Map<String, Object> annotations;

    /**
     * Declares how this tool may be invoked over MCP (direct tools/call vs dsl_execute_plan steps only).
     * Present in schema files as {@code x-textellent-mcp}; echoed in tools/list for clients.
     */
    @JsonProperty("x-textellent-mcp")
    private Map<String, Object> textellentMcp;

    // Keep internal fields for backwards compatibility and internal logic
    @JsonIgnore
    private Boolean readOnly;

    @JsonIgnore
    private Boolean destructive;

    @JsonIgnore
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
        updateAnnotations();
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
        updateAnnotations();
    }

    public Boolean getDestructive() {
        return destructive;
    }

    public void setDestructive(Boolean destructive) {
        this.destructive = destructive;
        updateAnnotations();
    }

    public String getRequiredScope() {
        return requiredScope;
    }

    public void setRequiredScope(String requiredScope) {
        this.requiredScope = requiredScope;
    }

    public Map<String, Object> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, Object> annotations) {
        this.annotations = annotations;
    }

    public Map<String, Object> getTextellentMcp() {
        return textellentMcp;
    }

    public void setTextellentMcp(Map<String, Object> textellentMcp) {
        this.textellentMcp = textellentMcp;
    }

    /**
     * Update the MCP-compliant annotations object based on internal fields.
     * MCP spec fields:
     * - readOnlyHint: true if the tool only reads data (no side effects)
     * - destructiveHint: true if the tool can delete/modify data permanently
     * - idempotentHint: true if calling multiple times has same effect as once
     * - openWorldHint: true if the tool interacts with external world
     */
    private void updateAnnotations() {
        if (this.annotations == null) {
            this.annotations = new HashMap<>();
        }

        // readOnlyHint: true for GET/list operations that don't modify data
        if (this.readOnly != null) {
            this.annotations.put("readOnlyHint", this.readOnly);
        }

        // destructiveHint: true for DELETE/UPDATE operations
        if (this.destructive != null) {
            this.annotations.put("destructiveHint", this.destructive);
        }

        // idempotentHint: read-only operations are always idempotent
        // For write operations, assume they may not be idempotent (safer default)
        this.annotations.put("idempotentHint", Boolean.TRUE.equals(this.readOnly));

        // openWorldHint: all our tools interact with external Textellent API
        this.annotations.put("openWorldHint", true);
    }
}
