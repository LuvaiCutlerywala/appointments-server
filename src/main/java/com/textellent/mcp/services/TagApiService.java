package com.textellent.mcp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
        logger.info("Getting all tags");

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

            return response;
        } catch (Exception e) {
            logger.error("Failed to get all tags", e);
            throw new RuntimeException("Failed to get all tags: " + e.getMessage(), e);
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
