package com.textellent.mcp.services.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolves {{...}} template expressions in DSL plan arguments against vars and optional batch context.
 * Used by both simplePlan and task executors when building tool call arguments.
 */
@Component
public class DslTemplateResolver {

    private static final Logger logger = LoggerFactory.getLogger(DslTemplateResolver.class);

    private final ObjectMapper objectMapper;

    public DslTemplateResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Recursively resolve template strings in a map (e.g. step args). Mutates nothing; returns new map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveTemplatesInArgs(
            Map<String, Object> args,
            Map<String, Object> vars,
            Map<String, Object> batchContext
    ) {
        if (args == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.startsWith("{{") && s.endsWith("}}")) {
                    String expr = s.substring(2, s.length() - 2).trim();
                    Object resolvedValue = resolveExpression(expr, vars, batchContext);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Resolved template '{}' -> {}", expr, resolvedValue == null ? "null" : resolvedValue.getClass().getSimpleName());
                    }
                    resolved.put(entry.getKey(), resolvedValue);
                } else {
                    resolved.put(entry.getKey(), value);
                }
            } else if (value instanceof Map) {
                resolved.put(entry.getKey(), resolveTemplatesInArgs((Map<String, Object>) value, vars, batchContext));
            } else if (value instanceof List) {
                resolved.put(entry.getKey(), resolveTemplatesInList((List<Object>) value, vars, batchContext));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    public List<Object> resolveTemplatesInList(
            List<Object> list,
            Map<String, Object> vars,
            Map<String, Object> batchContext
    ) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<Object> resolved = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String) {
                String s = (String) item;
                if (s.startsWith("{{") && s.endsWith("}}")) {
                    String expr = s.substring(2, s.length() - 2).trim();
                    resolved.add(resolveExpression(expr, vars, batchContext));
                } else {
                    resolved.add(item);
                }
            } else if (item instanceof Map) {
                resolved.add(resolveTemplatesInArgs((Map<String, Object>) item, vars, batchContext));
            } else if (item instanceof List) {
                resolved.add(resolveTemplatesInList((List<Object>) item, vars, batchContext));
            } else {
                resolved.add(item);
            }
        }
        return resolved;
    }

    public Object resolveExpression(
            String expr,
            Map<String, Object> vars,
            Map<String, Object> batchContext
    ) {
        try {
            if (expr.startsWith("vars.")) {
                return resolvePath(vars, expr.substring("vars.".length()));
            }
            if (expr.startsWith("batch.") && batchContext != null) {
                String path = expr.substring("batch.".length());
                if ("items[*].id".equals(path)) {
                    Object itemsObj = batchContext.get("items");
                    if (itemsObj instanceof List) {
                        List<Object> ids = new ArrayList<>();
                        for (Object item : (List<?>) itemsObj) {
                            if (item instanceof Map) {
                                Object id = ((Map<?, ?>) item).get("id");
                                if (id != null) {
                                    ids.add(id);
                                }
                            }
                        }
                        return ids;
                    }
                }
                return resolvePath(batchContext, path);
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve expression '{}': {}", expr, e.getMessage());
        }
        return "{{" + expr + "}}";
    }

    public Object resolvePath(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        String[] segments = path.split("\\.");
        Object current = root;
        for (String segment : segments) {
            if (!(current instanceof Map)) {
                return null;
            }
            String key = segment;
            Integer index = null;
            int bracket = segment.indexOf('[');
            if (bracket > 0 && segment.endsWith("]")) {
                key = segment.substring(0, bracket);
                String idx = segment.substring(bracket + 1, segment.length() - 1);
                try {
                    index = Integer.parseInt(idx);
                } catch (NumberFormatException ignored) {
                    // treat as plain key
                }
            }
            current = ((Map<?, ?>) current).get(key);
            if (index != null) {
                if (current instanceof List) {
                    List<?> list = (List<?>) current;
                    if (index >= 0 && index < list.size()) {
                        current = list.get(index);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return current;
    }

    /**
     * Parse JSON string to object when tool returns a string; otherwise return as-is.
     */
    public Object maybeParseJson(Object raw) {
        if (raw instanceof String) {
            try {
                return objectMapper.readValue((String) raw, Object.class);
            } catch (Exception e) {
                logger.trace("Could not parse tool output as JSON, leaving as string: {}", e.getMessage());
                return raw;
            }
        }
        return raw;
    }
}
