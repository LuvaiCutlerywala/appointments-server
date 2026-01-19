package com.textellent.mcp.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.McpToolDefinition;
import com.textellent.mcp.services.*;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Registry for MCP tools that maps tool names to their handlers and definitions.
 */
@Component
public class McpToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);

    private final Map<String, McpToolHandler> handlers = new HashMap<>();
    private final Map<String, McpToolDefinition> toolDefinitions = new HashMap<>();
    private final Map<String, Schema> schemas = new HashMap<>();

    @Autowired
    private MessageApiService messageApiService;

    @Autowired
    private ContactApiService contactApiService;

    @Autowired
    private TagApiService tagApiService;

    @Autowired
    private AppointmentApiService appointmentApiService;

    @Autowired
    private CallbackEventApiService callbackEventApiService;

    @Autowired
    private ConfigurationApiService configurationApiService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registerAllTools();
        loadToolSchemas();
    }

    /**
     * Register all MCP tools with their handlers.
     */
    private void registerAllTools() {
        // Message tools
        registerTool("messages_send", messageApiService::sendMessage);

        // Contact tools
        registerTool("contacts_add", contactApiService::addContacts);
        registerTool("contacts_update", contactApiService::updateContact);
        registerTool("contacts_get_all", contactApiService::getAllContacts);
        registerTool("contacts_get", contactApiService::getContact);
        registerTool("contacts_delete", contactApiService::deleteContact);
        registerTool("contacts_find_multiple_phones", contactApiService::findContactWithMultiplePhoneNumbers);
        registerTool("contacts_find", contactApiService::findContact);

        // Tag tools
        registerTool("tags_create", tagApiService::createTag);
        registerTool("tags_update", tagApiService::updateTag);
        registerTool("tags_get", tagApiService::getTag);
        registerTool("tags_get_all", tagApiService::getAllTags);
        registerTool("tags_assign_contacts", tagApiService::assignContactsToTag);
        registerTool("tags_delete", tagApiService::deleteTag);
        registerTool("tags_remove_contacts", tagApiService::removeContactsFromTag);

        // Appointment tools
        registerTool("appointments_create", appointmentApiService::createAppointment);
        registerTool("appointments_update", appointmentApiService::updateAppointment);
        registerTool("appointments_cancel", appointmentApiService::cancelAppointment);

        // Callback event tools
        registerTool("events_phone_added_wrong_number", callbackEventApiService::getPhoneNumberAddedToWrongNumber);
        registerTool("events_outgoing_delivery_status", callbackEventApiService::getOutgoingMessageDeliveryStatus);
        registerTool("events_new_contact_details", callbackEventApiService::getNewContactDetails);
        registerTool("events_disassociate_contact_tag", callbackEventApiService::getDisassociateContactFromTag);
        registerTool("events_incoming_message", callbackEventApiService::getIncomingMessageDetail);
        registerTool("events_phone_added_dnt", callbackEventApiService::getPhoneNumberAddedToDNT);
        registerTool("events_associate_contact_tag", callbackEventApiService::getAssociateContactToTag);
        registerTool("events_appointment_created", callbackEventApiService::getAppointmentCreated);
        registerTool("events_appointment_updated", callbackEventApiService::getAppointmentUpdated);
        registerTool("events_appointment_canceled", callbackEventApiService::getAppointmentCanceled);
        registerTool("events_phone_removed_dnt", callbackEventApiService::getPhoneNumberRemovedFromDNT);

        // Configuration tools
        registerTool("webhook_subscribe", configurationApiService::webhookSubscribe);
        registerTool("webhook_unsubscribe", configurationApiService::webhookUnsubscribe);
        registerTool("webhook_list_subscriptions", configurationApiService::listSubscriptions);

        logger.info("Registered {} MCP tools", handlers.size());
    }

    /**
     * Register a tool with its handler.
     */
    private void registerTool(String toolName, McpToolHandler handler) {
        handlers.put(toolName, handler);
    }

    /**
     * Load all tool schemas from resources/schemas directory.
     */
    private void loadToolSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:schemas/*.json");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        String toolName = filename.replace(".json", "");

                        // Read the schema file as a map
                        Map<String, Object> schemaMap = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});

                        // Create tool definition
                        McpToolDefinition toolDef = new McpToolDefinition();
                        toolDef.setName(toolName);
                        toolDef.setDescription((String) schemaMap.get("description"));
                        toolDef.setInputSchema((Map<String, Object>) schemaMap.get("inputSchema"));
                        toolDef.setOutputSchema((Map<String, Object>) schemaMap.get("outputSchema"));

                        // Set safety metadata based on tool type
                        configureSafetyMetadata(toolName, toolDef);

                        toolDefinitions.put(toolName, toolDef);

                        // Load JSON schema validator for input validation
                        if (schemaMap.containsKey("inputSchema")) {
                            JSONObject jsonSchema = new JSONObject(schemaMap.get("inputSchema"));
                            Schema schema = SchemaLoader.load(jsonSchema);
                            schemas.put(toolName, schema);
                        }

                        logger.debug("Loaded schema for tool: {}", toolName);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load schema from resource: {}", resource.getFilename(), e);
                }
            }

            logger.info("Loaded {} tool schemas", toolDefinitions.size());
        } catch (IOException e) {
            logger.error("Failed to load tool schemas", e);
        }
    }

    /**
     * Get all registered tool definitions.
     */
    public List<McpToolDefinition> getAllToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /**
     * Execute a tool with the given name and arguments.
     */
    public Object execute(String toolName, Map<String, Object> arguments, String authCode, String partnerClientCode) throws Exception {
        McpToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        // Validate arguments against schema if available
        validateArguments(toolName, arguments);

        // Execute the tool
        return handler.execute(arguments, authCode, partnerClientCode);
    }

    /**
     * Validate arguments against the tool's input schema.
     */
    private void validateArguments(String toolName, Map<String, Object> arguments) throws ValidationException {
        Schema schema = schemas.get(toolName);
        if (schema != null && arguments != null) {
            JSONObject jsonObject = new JSONObject(arguments);
            schema.validate(jsonObject);
        }
    }

    /**
     * Check if a tool exists in the registry.
     */
    public boolean hasTool(String toolName) {
        return handlers.containsKey(toolName);
    }

    /**
     * Get a specific tool definition by name.
     */
    public McpToolDefinition getToolDefinition(String toolName) {
        return toolDefinitions.get(toolName);
    }

    /**
     * Configure safety metadata for a tool based on its name and function.
     */
    private void configureSafetyMetadata(String toolName, McpToolDefinition toolDef) {
        // Read-only tools (GET operations, list operations)
        if (toolName.startsWith("contacts_get") || toolName.startsWith("tags_get") ||
            toolName.startsWith("events_") || toolName.equals("webhook_list_subscriptions") ||
            toolName.equals("contacts_find") || toolName.equals("contacts_find_multiple_phones")) {
            toolDef.setReadOnly(true);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("read"); // Changed from textellent.read to match OAuth2 scopes
        }
        // Destructive tools (DELETE operations)
        else if (toolName.contains("_delete") || toolName.contains("_cancel") ||
                 toolName.equals("webhook_unsubscribe") || toolName.equals("tags_remove_contacts")) {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(true);
            toolDef.setRequiredScope("write"); // Changed from textellent.write to match OAuth2 scopes
        }
        // Write tools (CREATE/UPDATE operations)
        else {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("write"); // Changed from textellent.write to match OAuth2 scopes
        }
    }
}
