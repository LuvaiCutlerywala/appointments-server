package com.textellent.mcp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for Textellent Configuration API operations.
 */
@Service
public class ConfigurationApiService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Subscribe to a webhook event.
     * POST /api/v1/event/subscribe.json
     */
    public Object webhookSubscribe(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Subscribing to webhook with arguments: {}", arguments);

        try {
            String targetUrl = (String) arguments.get("target_url");
            String event = (String) arguments.get("event");

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("target_url", targetUrl);
            requestBody.put("event", event);

            String response = webClient.post()
                    .uri("/api/v1/event/subscribe.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error subscribing to webhook", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to subscribe to webhook", e);
            throw new RuntimeException("Failed to subscribe to webhook: " + e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe from a webhook event.
     * DELETE /api/v1/event/unsubscribe/{clientId}.json
     */
    public Object webhookUnsubscribe(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Unsubscribing from webhook with arguments: {}", arguments);

        try {
            String clientId = (String) arguments.get("clientId");
            String eventType = (String) arguments.get("event");
            String targetUrl = (String) arguments.get("targetUrl");

            String response = webClient.delete()
                    .uri("/api/v1/event/unsubscribe/" + clientId + ".json")
                    .header("event", eventType)
                    .header("targetUrl", targetUrl)
                    .header("authCode", authCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error unsubscribing from webhook", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to unsubscribe from webhook", e);
            throw new RuntimeException("Failed to unsubscribe from webhook: " + e.getMessage(), e);
        }
    }

    /**
     * List all webhook subscriptions.
     * GET /api/v1/event/subscriptions.json
     */
    public Object listSubscriptions(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Listing webhook subscriptions");

        try {
            String response = webClient.get()
                    .uri("/api/v1/event/subscriptions.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error listing webhook subscriptions", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to list webhook subscriptions", e);
            throw new RuntimeException("Failed to list webhook subscriptions: " + e.getMessage(), e);
        }
    }
}
