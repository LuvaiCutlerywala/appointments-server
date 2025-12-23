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
 * Service for Textellent Message API operations.
 */
@Service
public class MessageApiService {

    private static final Logger logger = LoggerFactory.getLogger(MessageApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Send a message using Textellent API.
     * POST /api/v1/messages.json
     */
    public Object sendMessage(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Sending message with arguments: {}", arguments);

        try {
            String response = webClient.post()
                    .uri("/api/v1/messages.json")
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(arguments)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error sending message", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to send message", e);
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }
}
