package com.textellent.mcp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for Textellent Callback Event API operations.
 */
@Service
public class CallbackEventApiService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackEventApiService.class);

    @Autowired
    private WebClient webClient;

    /**
     * Get phone number added to wrong number events.
     * GET /api/v1/events/phoneNumberAddedToWrongNumber.json?limit={limit}
     */
    public Object getPhoneNumberAddedToWrongNumber(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting phone number added to wrong number events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/phoneNumberAddedToWrongNumber.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting phone number added to wrong number events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get phone number added to wrong number events", e);
            throw new RuntimeException("Failed to get phone number added to wrong number events: " + e.getMessage(), e);
        }
    }

    /**
     * Get outgoing message delivery status events.
     * GET /api/v1/events/outgoingMessageDeliveryStatus.json?limit={limit}
     */
    public Object getOutgoingMessageDeliveryStatus(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting outgoing message delivery status events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/outgoingMessageDeliveryStatus.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting outgoing message delivery status events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get outgoing message delivery status events", e);
            throw new RuntimeException("Failed to get outgoing message delivery status events: " + e.getMessage(), e);
        }
    }

    /**
     * Get new contact details events.
     * GET /api/v1/events/newContactDetails.json?limit={limit}
     */
    public Object getNewContactDetails(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting new contact details events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/newContactDetails.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting new contact details events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get new contact details events", e);
            throw new RuntimeException("Failed to get new contact details events: " + e.getMessage(), e);
        }
    }

    /**
     * Get disassociate contact from tag events.
     * GET /api/v1/events/disassociateContactFromTag.json?limit={limit}
     */
    public Object getDisassociateContactFromTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting disassociate contact from tag events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/disassociateContactFromTag.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting disassociate contact from tag events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get disassociate contact from tag events", e);
            throw new RuntimeException("Failed to get disassociate contact from tag events: " + e.getMessage(), e);
        }
    }

    /**
     * Get incoming message detail events.
     * GET /api/v1/events/incomingMessageDetail.json?limit={limit}
     */
    public Object getIncomingMessageDetail(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting incoming message detail events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/incomingMessageDetail.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting incoming message detail events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get incoming message detail events", e);
            throw new RuntimeException("Failed to get incoming message detail events: " + e.getMessage(), e);
        }
    }

    /**
     * Get phone number added to DNT events.
     * GET /api/v1/events/phoneNumberAddedToDNT.json?limit={limit}
     */
    public Object getPhoneNumberAddedToDNT(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting phone number added to DNT events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/phoneNumberAddedToDNT.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting phone number added to DNT events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get phone number added to DNT events", e);
            throw new RuntimeException("Failed to get phone number added to DNT events: " + e.getMessage(), e);
        }
    }

    /**
     * Get associate contact to tag events.
     * GET /api/v1/events/associateContactToTag.json?limit={limit}
     */
    public Object getAssociateContactToTag(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting associate contact to tag events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/associateContactToTag.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting associate contact to tag events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get associate contact to tag events", e);
            throw new RuntimeException("Failed to get associate contact to tag events: " + e.getMessage(), e);
        }
    }

    /**
     * Get appointment created events.
     * GET /api/v1/events/appointmentCreated.json?limit={limit}
     */
    public Object getAppointmentCreated(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting appointment created events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/appointmentCreated.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting appointment created events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get appointment created events", e);
            throw new RuntimeException("Failed to get appointment created events: " + e.getMessage(), e);
        }
    }

    /**
     * Get appointment updated events.
     * GET /api/v1/events/appointmentUpdated.json.json?limit={limit}
     */
    public Object getAppointmentUpdated(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting appointment updated events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/appointmentUpdated.json.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting appointment updated events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get appointment updated events", e);
            throw new RuntimeException("Failed to get appointment updated events: " + e.getMessage(), e);
        }
    }

    /**
     * Get appointment canceled events.
     * GET /api/v1/events/appointmentCanceled.json.json?limit={limit}
     */
    public Object getAppointmentCanceled(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting appointment canceled events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/appointmentCanceled.json.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting appointment canceled events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get appointment canceled events", e);
            throw new RuntimeException("Failed to get appointment canceled events: " + e.getMessage(), e);
        }
    }

    /**
     * Get phone number removed from DNT events.
     * GET /api/v1/events/phoneNumberRemovedFromDNT.json?limit={limit}
     */
    public Object getPhoneNumberRemovedFromDNT(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("Getting phone number removed from DNT events");

        try {
            Integer limit = (Integer) arguments.getOrDefault("limit", 10);

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/events/phoneNumberRemovedFromDNT.json")
                            .queryParam("limit", limit)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("authCode", authCode)
                    .header("partnerClientCode", partnerClientCode)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        logger.error("Error getting phone number removed from DNT events", e);
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return response;
        } catch (Exception e) {
            logger.error("Failed to get phone number removed from DNT events", e);
            throw new RuntimeException("Failed to get phone number removed from DNT events: " + e.getMessage(), e);
        }
    }
}
