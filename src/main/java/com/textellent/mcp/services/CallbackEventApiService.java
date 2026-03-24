package com.textellent.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.services.pagination.TextellentPagedListMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for Textellent Callback Event API operations.
 * List endpoints use pageNum + limit with {@link TextellentPagedListMerger} so all pages are merged.
 * Tool entry points are registered via lambdas in {@link com.textellent.mcp.registry.McpToolRegistry}.
 */
@Service
public class CallbackEventApiService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackEventApiService.class);

    @Autowired
    private WebClient webClient;

    private static int resolveEventPageSize(Map<String, Object> arguments) {
        if (arguments == null) {
            return 100;
        }
        Object lim = arguments.get("limit");
        if (lim instanceof Number && ((Number) lim).intValue() > 0) {
            return Math.min(500, Math.max(50, ((Number) lim).intValue()));
        }
        return 100;
    }

    /**
     * GET path with {@code limit} (page size) and {@code pageNum}; merged into one {@code events} array.
     */
    public Object fetchPagedEvents(String path, Map<String, Object> arguments, String authCode, String partnerClientCode)
            throws Exception {
        int perPage = resolveEventPageSize(arguments);
        ObjectMapper mapper = new ObjectMapper();
        return TextellentPagedListMerger.mergeToJsonWithTotalCount(
                mapper,
                logger,
                path,
                pageNum -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(path)
                                .queryParam("limit", perPage)
                                .queryParam("pageNum", pageNum)
                                .build())
                        .header("Content-Type", "application/json")
                        .header("authCode", authCode)
                        .header("partnerClientCode", partnerClientCode)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorResume(e -> {
                            logger.error("Error getting events {} page {}", path, pageNum, e);
                            return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                        })
                        .block(),
                TextellentPagedListMerger::parseEventsListPage,
                "events",
                TextellentPagedListMerger.DEFAULT_MAX_PAGES
        );
    }
}
