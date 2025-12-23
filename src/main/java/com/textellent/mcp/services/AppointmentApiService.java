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
 * Service for Textellent Appointment API operations.
 */
@Service
public class AppointmentApiService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Create an appointment using Textellent API.
     * POST /api/v1/createAppointment.json?source={source}&extId={extId}
     */
    public Object createAppointment(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Creating appointment with arguments: {}", arguments);

        try {
            String source = (String) arguments.getOrDefault("source", "");
            String extId = (String) arguments.getOrDefault("extId", "");
            Map<String, Object> appointmentData = (Map<String, Object>) arguments.get("appointmentData");

            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/createAppointment.json")
                            .queryParam("source", source)
                            .queryParam("extId", extId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(appointmentData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error creating appointment", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to create appointment", e);
            throw new RuntimeException("Failed to create appointment: " + e.getMessage(), e);
        }
    }

    /**
     * Update an appointment using Textellent API.
     * PUT /api/v1/updateAppointment.json?source={source}&extId={extId}
     */
    public Object updateAppointment(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Updating appointment with arguments: {}", arguments);

        try {
            String source = (String) arguments.getOrDefault("source", "");
            String extId = (String) arguments.getOrDefault("extId", "");
            Map<String, Object> appointmentData = (Map<String, Object>) arguments.get("appointmentData");

            String response = webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/updateAppointment.json")
                            .queryParam("source", source)
                            .queryParam("extId", extId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(appointmentData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error updating appointment", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to update appointment", e);
            throw new RuntimeException("Failed to update appointment: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel an appointment using Textellent API.
     * DELETE /api/v1/cancelAppointment.json?source={source}&extId={extId}
     */
    public Object cancelAppointment(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Canceling appointment with arguments: {}", arguments);

        try {
            String source = (String) arguments.getOrDefault("source", "");
            String extId = (String) arguments.getOrDefault("extId", "");

            String response = webClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/cancelAppointment.json")
                            .queryParam("source", source)
                            .queryParam("extId", extId)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error canceling appointment", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to cancel appointment", e);
            throw new RuntimeException("Failed to cancel appointment: " + e.getMessage(), e);
        }
    }
}
