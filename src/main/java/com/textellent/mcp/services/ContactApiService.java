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
 * Service for Textellent Contact API operations.
 */
@Service
public class ContactApiService {

    private static final Logger logger = LoggerFactory.getLogger(ContactApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Add contacts using Textellent API.
     * POST /api/v1/contacts.json
     */
    public Object addContacts(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Adding contacts with arguments: {}", arguments);

        try {
            Object contacts = arguments.get("contacts");

            String response = webClient.post()
                    .uri("/api/v1/contacts.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contacts)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error adding contacts", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to add contacts", e);
            throw new RuntimeException("Failed to add contacts: " + e.getMessage(), e);
        }
    }

    /**
     * Update a contact using Textellent API.
     * PUT /api/v1/contacts.json?contactId={contactId}
     */
    public Object updateContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Updating contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");
            Map<String, Object> contactData = (Map<String, Object>) arguments.get("contactData");

            String response = webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/contacts.json")
                            .queryParam("contactId", contactId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(contactData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error updating contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to update contact", e);
            throw new RuntimeException("Failed to update contact: " + e.getMessage(), e);
        }
    }

    /**
     * Get all contacts using Textellent API.
     * GET /api/v1/contacts.json?searchKey={searchKey}&pageSize={pageSize}&pageNum={pageNum}
     */
    public Object getAllContacts(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting all contacts with arguments: {}", arguments);

        try {
            String searchKey = (String) arguments.getOrDefault("searchKey", "");
            Integer pageSize = (Integer) arguments.getOrDefault("pageSize", 10);
            Integer pageNum = (Integer) arguments.getOrDefault("pageNum", 1);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/contacts.json")
                            .queryParam("searchKey", searchKey)
                            .queryParam("pageSize", pageSize)
                            .queryParam("pageNum", pageNum)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting all contacts", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get all contacts", e);
            throw new RuntimeException("Failed to get all contacts: " + e.getMessage(), e);
        }
    }

    /**
     * Get a specific contact by ID using Textellent API.
     * GET /api/v1/contacts/{contactId}.json
     */
    public Object getContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");

            String response = webClient.get()
                    .uri("/api/v1/contacts/" + contactId + ".json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get contact", e);
            throw new RuntimeException("Failed to get contact: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a contact by ID using Textellent API.
     * DELETE /api/v1/contacts/{contactId}.json
     */
    public Object deleteContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Deleting contact with arguments: {}", arguments);

        try {
            String contactId = (String) arguments.get("contactId");

            String response = webClient.delete()
                    .uri("/api/v1/contacts/" + contactId + ".json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error deleting contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to delete contact", e);
            throw new RuntimeException("Failed to delete contact: " + e.getMessage(), e);
        }
    }

    /**
     * Find contact with multiple phone numbers using Textellent API.
     * GET /api/v1/findContactWithMultiplePhoneNumbers.json?extId={extId}&phoneNumbers={phoneNumbers}
     */
    public Object findContactWithMultiplePhoneNumbers(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Finding contact with multiple phone numbers: {}", arguments);

        try {
            String extId = (String) arguments.getOrDefault("extId", "");
            String phoneNumbers = (String) arguments.get("phoneNumbers");

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/findContactWithMultiplePhoneNumbers.json")
                            .queryParam("extId", extId)
                            .queryParam("phoneNumbers", phoneNumbers)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error finding contact with multiple phone numbers", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to find contact with multiple phone numbers", e);
            throw new RuntimeException("Failed to find contact with multiple phone numbers: " + e.getMessage(), e);
        }
    }

    /**
     * Find contact using Textellent API.
     * GET /api/v1/findContact.json?extId={extId}&phoneNumber={phoneNumber}&email={email}
     */
    public Object findContact(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Finding contact with arguments: {}", arguments);

        try {
            String extId = (String) arguments.getOrDefault("extId", "");
            String phoneNumber = (String) arguments.getOrDefault("phoneNumber", "");
            String email = (String) arguments.getOrDefault("email", "");

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/findContact.json")
                            .queryParam("extId", extId)
                            .queryParam("phoneNumber", phoneNumber)
                            .queryParam("email", email)
                            .build())
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error finding contact", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to find contact", e);
            throw new RuntimeException("Failed to find contact: " + e.getMessage(), e);
        }
    }
}
