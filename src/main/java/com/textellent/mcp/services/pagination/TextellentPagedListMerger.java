package com.textellent.mcp.services.pagination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges Textellent list API pages (pageNum-based) into a single in-memory list and serializes
 * a consistent JSON shape for tools. Stops when a page is partial or empty; does not trust
 * {@code totalCount} alone to decide whether more pages exist.
 */
public final class TextellentPagedListMerger {

    public static final int DEFAULT_MAX_PAGES = 1000;

    private TextellentPagedListMerger() {
    }

    /**
     * One page of rows plus optional API hints (pageSize / totalCount may be 0 when unknown).
     */
    public static final class PageRowsResult {
        public final List<Map<String, Object>> rows;
        public final int apiPageSize;
        public final int apiReportedTotal;

        public PageRowsResult(List<Map<String, Object>> rows, int apiPageSize, int apiReportedTotal) {
            this.rows = rows != null ? rows : Collections.emptyList();
            this.apiPageSize = apiPageSize;
            this.apiReportedTotal = apiReportedTotal;
        }
    }

    @FunctionalInterface
    public interface PageFetcher {
        String fetch(int pageNum);
    }

    @FunctionalInterface
    public interface PageRowsParser {
        /**
         * @return null if the body cannot be parsed as a list page; otherwise a result (possibly empty rows)
         */
        PageRowsResult parse(String responseBody, ObjectMapper mapper) throws Exception;
    }

    public static final class MergeOrRaw {
        public final List<Map<String, Object>> merged;
        public final String rawFirstPageIfUnparsed;

        private MergeOrRaw(List<Map<String, Object>> merged, String rawFirstPageIfUnparsed) {
            this.merged = merged;
            this.rawFirstPageIfUnparsed = rawFirstPageIfUnparsed;
        }
    }

    /**
     * Fetch all pages, merge rows, return JSON {@code { "<arrayKey>": [...], "totalCount": n }}.
     * If page 1 cannot be parsed, returns the raw first-page response string unchanged (no second fetch).
     */
    public static String mergeToJsonWithTotalCount(
            ObjectMapper mapper,
            Logger log,
            String logLabel,
            PageFetcher fetcher,
            PageRowsParser parser,
            String arrayKeyInOutput,
            int maxPages
    ) throws Exception {
        MergeOrRaw outcome = mergeAllRowsOrRaw(mapper, log, logLabel, fetcher, parser, maxPages);
        if (outcome.rawFirstPageIfUnparsed != null) {
            return outcome.rawFirstPageIfUnparsed;
        }
        if (outcome.merged == null) {
            return null;
        }
        Map<String, Object> out = new HashMap<>();
        out.put(arrayKeyInOutput, outcome.merged);
        out.put("totalCount", outcome.merged.size());
        return mapper.writeValueAsString(out);
    }

    /**
     * @return merged rows, or {@code rawFirstPageIfUnparsed} set when page 1 could not be parsed or was null body
     */
    public static MergeOrRaw mergeAllRowsOrRaw(
            ObjectMapper mapper,
            Logger log,
            String logLabel,
            PageFetcher fetcher,
            PageRowsParser parser,
            int maxPages
    ) throws Exception {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int fullPageRowCount = -1;
        int apiReportedTotalOnFirstPage = -1;

        for (int pageNum = 1; pageNum <= maxPages; pageNum++) {
            String pageResponse = fetcher.fetch(pageNum);
            if (pageResponse == null) {
                if (pageNum == 1) {
                    log.warn("{}: no response for page 1", logLabel);
                    return new MergeOrRaw(null, null);
                }
                break;
            }

            PageRowsResult page = parser.parse(pageResponse, mapper);
            if (page == null) {
                if (pageNum == 1) {
                    log.warn("{}: could not parse page 1", logLabel);
                    return new MergeOrRaw(null, pageResponse);
                }
                break;
            }

            if (page.rows.isEmpty()) {
                if (pageNum == 1) {
                    return new MergeOrRaw(new ArrayList<>(), null);
                }
                break;
            }

            if (pageNum == 1 && page.apiReportedTotal > 0) {
                apiReportedTotalOnFirstPage = page.apiReportedTotal;
            }

            allRows.addAll(page.rows);
            int thisPageCount = page.rows.size();

            if (fullPageRowCount < 0) {
                int apiPageSize = page.apiPageSize > 0 ? page.apiPageSize : Integer.MAX_VALUE;
                fullPageRowCount = Math.min(apiPageSize, thisPageCount);
                if (fullPageRowCount <= 0) {
                    fullPageRowCount = thisPageCount;
                }
                log.info("{}: inferred full-page row count {} (api pageSize={}, first page rows={})",
                        logLabel, fullPageRowCount, page.apiPageSize, thisPageCount);
            }

            if (thisPageCount < fullPageRowCount) {
                log.debug("{}: partial page {} ({} rows), stopping", logLabel, pageNum, thisPageCount);
                break;
            }

            log.debug("{}: full page {} ({} rows), may fetch more", logLabel, pageNum, thisPageCount);

            if (pageNum == maxPages) {
                log.warn("{}: hit max page cap ({}); more rows may exist on the API", logLabel, maxPages);
                break;
            }
        }

        int mergedCount = allRows.size();
        if (apiReportedTotalOnFirstPage > 0 && apiReportedTotalOnFirstPage != mergedCount) {
            log.info("{}: API first page reported totalCount={}; merged {} rows (response totalCount uses merged size)",
                    logLabel, apiReportedTotalOnFirstPage, mergedCount);
        }

        log.info("{}: returning {} merged rows", logLabel, mergedCount);
        return new MergeOrRaw(allRows, null);
    }

    public static int parseOptionalNonNegativeInt(JsonNode node, int defaultVal) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return defaultVal;
        }
        if (node.isIntegralNumber()) {
            long v = node.longValue();
            if (v >= 0 && v <= Integer.MAX_VALUE) {
                return (int) v;
            }
            return defaultVal;
        }
        double d = node.asDouble();
        if (d >= 0 && d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        return defaultVal;
    }

    public static int parseOptionalPositiveInt(JsonNode node, int defaultVal) {
        int n = parseOptionalNonNegativeInt(node, defaultVal);
        return n > 0 ? n : defaultVal;
    }

    /**
     * Contacts list: {@code data.contacts} or root array, with optional text/data wrappers.
     */
    public static PageRowsResult parseContactsListPage(String response, ObjectMapper mapper) {
        try {
            JsonNode rootNode = mapper.readTree(response);
            if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                return null;
            }
            JsonNode dataNode = unwrapTextAndData(rootNode, mapper);
            if (dataNode == null) {
                return null;
            }

            JsonNode arrayNode;
            JsonNode totalCountNode = null;
            JsonNode pageSizeNode = null;
            if (dataNode.isArray()) {
                arrayNode = dataNode;
            } else {
                arrayNode = dataNode.get("contacts");
                totalCountNode = dataNode.get("totalCount");
                pageSizeNode = dataNode.get("pageSize");
            }

            if (arrayNode == null || !arrayNode.isArray()) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = mapper.convertValue(arrayNode, List.class);
            int total = parseOptionalNonNegativeInt(totalCountNode, 0);
            int pageSize = parseOptionalNonNegativeInt(pageSizeNode, 0);
            return new PageRowsResult(rows, pageSize, total);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tags list: root array, {@code tags}, or text-wrapped JSON with the same.
     */
    public static PageRowsResult parseTagsListPage(String response, ObjectMapper mapper) {
        try {
            JsonNode rootNode = mapper.readTree(response);
            if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                return null;
            }

            JsonNode tagsNode;
            if (rootNode.isArray()) {
                tagsNode = rootNode;
            } else if (rootNode.has("text")) {
                JsonNode parsedText = mapper.readTree(rootNode.get("text").asText());
                if (parsedText.isArray()) {
                    tagsNode = parsedText;
                } else {
                    tagsNode = parsedText.get("tags");
                }
            } else {
                tagsNode = rootNode.get("tags");
            }

            if (tagsNode == null || !tagsNode.isArray()) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = mapper.convertValue(tagsNode, List.class);
            return new PageRowsResult(rows, 0, 0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Callback events: {@code events} array or root array, with text/data wrappers.
     */
    public static PageRowsResult parseEventsListPage(String response, ObjectMapper mapper) {
        return parseArrayPage(response, mapper, "events", "items", "data");
    }

    /**
     * Webhook subscriptions and similar: try common property names then root array.
     */
    public static PageRowsResult parseSubscriptionsListPage(String response, ObjectMapper mapper) {
        return parseArrayPage(response, mapper, "subscriptions", "subscriptionList", "items", "results");
    }

    private static PageRowsResult parseArrayPage(
            String response,
            ObjectMapper mapper,
            String firstKey,
            String... moreKeys
    ) {
        try {
            JsonNode rootNode = mapper.readTree(response);
            if (rootNode.has("error") && rootNode.get("error").isTextual()) {
                return null;
            }
            JsonNode dataNode = unwrapTextAndData(rootNode, mapper);
            if (dataNode == null) {
                return null;
            }

            JsonNode arrayNode = null;
            JsonNode pageSizeNode = null;
            JsonNode totalCountNode = null;

            if (dataNode.isArray()) {
                arrayNode = dataNode;
            } else {
                arrayNode = dataNode.get(firstKey);
                pageSizeNode = dataNode.get("pageSize");
                totalCountNode = dataNode.get("totalCount");
                if ((arrayNode == null || !arrayNode.isArray()) && moreKeys != null) {
                    for (String k : moreKeys) {
                        JsonNode n = dataNode.get(k);
                        if (n != null && n.isArray()) {
                            arrayNode = n;
                            break;
                        }
                    }
                }
            }

            if (arrayNode == null || !arrayNode.isArray()) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = mapper.convertValue(arrayNode, List.class);
            int pageSize = parseOptionalNonNegativeInt(pageSizeNode, 0);
            int total = parseOptionalNonNegativeInt(totalCountNode, 0);
            return new PageRowsResult(rows, pageSize, total);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode unwrapTextAndData(JsonNode rootNode, ObjectMapper mapper) throws java.io.IOException {
        JsonNode dataNode = rootNode;
        if (rootNode.has("text")) {
            dataNode = mapper.readTree(rootNode.get("text").asText());
        }
        if (dataNode.has("data")) {
            dataNode = dataNode.get("data");
        }
        return dataNode;
    }
}
