package com.textellent.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Textellent Tag API operations.
 */
@Service
public class TagApiService {

    private static final Logger logger = LoggerFactory.getLogger(TagApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Create contact tags using Textellent API.
     * POST /api/v1/tags.json
     */
    public Object createTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Creating tags with arguments: {}", arguments);

        try {
            Object tags = arguments.get("tags");

            String response = webClient.post()
                    .uri("/api/v1/tags.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(tags)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error creating tags", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to create tags", e);
            throw new RuntimeException("Failed to create tags: " + e.getMessage(), e);
        }
    }

    /**
     * Update a contact tag using Textellent API.
     * PUT /api/v1/tags.json?tagId={tagId}
     */
    public Object updateTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Updating tag with arguments: {}", arguments);

        try {
            String tagId = (String) arguments.get("tagId");
            Map<String, Object> tagData = (Map<String, Object>) arguments.get("tagData");

            String response = webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/tags.json")
                            .queryParam("tagId", tagId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(tagData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error updating tag", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to update tag", e);
            throw new RuntimeException("Failed to update tag: " + e.getMessage(), e);
        }
    }

    /**
     * Get a specific contact tag by ID using Textellent API.
     * GET /api/v1/tags/{tagId}.json
     */
    public Object getTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting tag with arguments: {}", arguments);

        try {
            String tagId = (String) arguments.get("tagId");

            String response = webClient.get()
                    .uri("/api/v1/tags/" + tagId + ".json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting tag", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get tag", e);
            throw new RuntimeException("Failed to get tag: " + e.getMessage(), e);
        }
    }

    /**
     * Get all contact tags using Textellent API.
     * GET /api/v1/tags.json
     */
    public Object getAllTags(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting all tags with arguments: {}", arguments);

        try {
            String response = webClient.get()
                    .uri("/api/v1/tags.json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting all tags", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            // Parse response and return full tag list (names only)
            if (response != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response);

                    // Check if this is an actual error response
                    if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                        logger.warn("Backend returned error: {}", response);
                        return response;
                    }

                    // Handle multiple response formats:
                    // 1. Direct array: [ { "tagName": "..." }, ... ]
                    // 2. Object with tags property: { "tags": [ ... ] }
                    // 3. Object with text property containing JSON string: { "text": "[...]" }
                    JsonNode tagsNode;
                    if (rootNode.isArray()) {
                        tagsNode = rootNode;
                    } else if (rootNode.has("text")) {
                        // Backend returned { "text": "[...]" } - parse the text field
                        logger.info("Response has 'text' field, parsing as JSON");
                        String textContent = rootNode.get("text").asText();
                        JsonNode parsedText = mapper.readTree(textContent);
                        if (parsedText.isArray()) {
                            tagsNode = parsedText;
                        } else {
                            tagsNode = parsedText.get("tags");
                        }
                    } else {
                        tagsNode = rootNode.get("tags");
                    }

                    if (tagsNode != null && tagsNode.isArray()) {
                        // Extract ONLY tag names to reduce response size and prevent hallucination
                        List<String> tagNames = new ArrayList<>();
                        for (JsonNode tag : tagsNode) {
                            JsonNode nameNode = tag.get("tagName");
                            if (nameNode != null) {
                                tagNames.add(nameNode.asText());
                            }
                        }

                        // Build response with NAMES ONLY
                        Map<String, Object> fullResponse = new HashMap<>();
                        fullResponse.put("tagNames", tagNames);  // Names only, not full objects
                        fullResponse.put("total", tagNames.size());

                        logger.info("Returning {} tag names", tagNames.size());

                        return mapper.writeValueAsString(fullResponse);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse/paginate tags response, returning raw response", e);
                    return response;
                }
            }

            // Should not reach here
            logger.warn("No valid tags array found in response");
            return response;
        } catch (Exception e) {
            logger.error("Failed to get all tags", e);
            throw new RuntimeException("Failed to get all tags: " + e.getMessage(), e);
        }
    }

    /**
     * Get a summary of tags (names and count only) to avoid ChatGPT hallucination.
     * If tagName parameter is provided, checks if that specific tag exists.
     * If tagName is omitted, returns all tag names.
     * GET /api/v1/tags.json
     */
    public Object getTagsSummary(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        String searchTagName = (String) arguments.get("tagName");
        logger.info("Getting tags summary, searchTagName: {}", searchTagName);

        try {
            String response = webClient.get()
                    .uri("/api/v1/tags.json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting tags summary", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            logger.info("Received response, length: {}", response != null ? response.length() : "null");

            // Parse response and extract only names
            if (response != null) {
                try {
                    logger.info("Parsing JSON response...");
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response);
                    logger.info("Parsed JSON, root is array: {}", rootNode.isArray());

                    // Check if this is an actual error response
                    if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                        logger.warn("Backend returned error: {}", response);
                        return response;
                    }

                    // Handle multiple response formats:
                    // 1. Direct array: [ { "tagName": "..." }, ... ]
                    // 2. Object with tags property: { "tags": [ ... ] }
                    // 3. Object with text property containing JSON string: { "text": "[...]" }
                    JsonNode tagsNode;
                    if (rootNode.isArray()) {
                        logger.info("Response is array format");
                        tagsNode = rootNode;
                    } else if (rootNode.has("text")) {
                        // Backend returned { "text": "[...]" } - parse the text field
                        logger.info("Response has 'text' field, parsing as JSON");
                        String textContent = rootNode.get("text").asText();
                        JsonNode parsedText = mapper.readTree(textContent);
                        if (parsedText.isArray()) {
                            tagsNode = parsedText;
                        } else {
                            tagsNode = parsedText.get("tags");
                        }
                    } else {
                        logger.info("Response is object format, looking for 'tags' property");
                        tagsNode = rootNode.get("tags");
                    }

                    if (tagsNode != null && tagsNode.isArray()) {
                        logger.info("Found tags array with {} elements", tagsNode.size());

                        // If searching for a specific tag name
                        if (searchTagName != null && !searchTagName.isEmpty()) {
                            String foundTagName = null;
                            for (JsonNode tag : tagsNode) {
                                JsonNode nameNode = tag.get("tagName");
                                if (nameNode != null) {
                                    String tagName = nameNode.asText();
                                    // Case-insensitive comparison
                                    if (tagName.equalsIgnoreCase(searchTagName)) {
                                        foundTagName = tagName; // Use exact case from database
                                        break;
                                    }
                                }
                            }

                            // Build response for specific tag search
                            Map<String, Object> searchResponse = new HashMap<>();
                            searchResponse.put("exists", foundTagName != null);
                            if (foundTagName != null) {
                                searchResponse.put("tagName", foundTagName);
                                searchResponse.put("total", 1);
                                List<String> matchingTags = new ArrayList<>();
                                matchingTags.add(foundTagName);
                                searchResponse.put("tagNames", matchingTags);
                            } else {
                                searchResponse.put("total", 0);
                                searchResponse.put("tagNames", new ArrayList<>());
                            }

                            logger.info("Tag '{}' exists: {}", searchTagName, foundTagName != null);
                            return mapper.writeValueAsString(searchResponse);
                        }

                        // No filter - return all tag names
                        List<String> tagNames = new ArrayList<>();
                        tagsNode.forEach(tag -> {
                            JsonNode nameNode = tag.get("tagName");
                            if (nameNode != null) {
                                tagNames.add(nameNode.asText());
                            }
                        });

                        // Build summary response
                        Map<String, Object> summaryResponse = new HashMap<>();
                        summaryResponse.put("total", tagNames.size());
                        summaryResponse.put("tagNames", tagNames);

                        logger.info("Returning summary of {} tags", tagNames.size());

                        return mapper.writeValueAsString(summaryResponse);
                    } else {
                        logger.warn("tagsNode is null or not an array: tagsNode={}", tagsNode);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse tags for summary, returning raw response", e);
                    return response;
                }
            } else {
                logger.warn("Response is null");
            }

            // Should not reach here
            logger.warn("No valid tags array found in response, returning raw");
            return response;
        } catch (Exception e) {
            logger.error("Failed to get tags summary", e);
            throw new RuntimeException("Failed to get tags summary: " + e.getMessage(), e);
        }
    }

    /**
     * Assign contacts to a tag using Textellent API.
     * POST /api/v1/tags/{tagName}/contacts.json
     */
    public Object assignContactsToTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Assigning contacts to tag with arguments: {}", arguments);

        try {
            String tagName = (String) arguments.get("tagName");
            Object contacts = arguments.get("contacts");

            String response = webClient.post()
                    .uri("/api/v1/tags/" + tagName + "/contacts.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contacts)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error assigning contacts to tag", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to assign contacts to tag", e);
            throw new RuntimeException("Failed to assign contacts to tag: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a contact tag by ID using Textellent API.
     * DELETE /api/v1/tags.json?tagId={tagId}
     */
    public Object deleteTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Deleting tag with arguments: {}", arguments);

        try {
            String tagId = (String) arguments.get("tagId");

            String response = webClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/tags.json")
                            .queryParam("tagId", tagId)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error deleting tag", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to delete tag", e);
            throw new RuntimeException("Failed to delete tag: " + e.getMessage(), e);
        }
    }

    /**
     * Remove contacts from a tag using Textellent API.
     * DELETE /api/v1/tags/{tagName}/contacts.json
     */
    public Object removeContactsFromTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Removing contacts from tag with arguments: {}", arguments);

        try {
            String tagName = (String) arguments.get("tagName");
            Object phoneNumbers = arguments.get("phoneNumbers");

            String response = webClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/api/v1/tags/" + tagName + "/contacts.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(phoneNumbers)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error removing contacts from tag", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to remove contacts from tag", e);
            throw new RuntimeException("Failed to remove contacts from tag: " + e.getMessage(), e);
        }
    }
}
