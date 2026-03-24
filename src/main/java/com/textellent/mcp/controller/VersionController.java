package com.textellent.mcp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Version endpoint for the MCP server.
 */
@RestController
public class VersionController {

    @Value("${mcp.server.name:textellent-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    @Value("${mcp.server.protocol-version:2025-06-18}")
    private String protocolVersion;

    @Value("${mcp.server.description:MCP Server exposing Textellent appointment APIs}")
    private String description;

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> version = new HashMap<>();
        version.put("name", serverName);
        version.put("version", serverVersion);
        version.put("protocolVersion", protocolVersion);
        version.put("description", description);
        version.put("apiVersion", "v1");
        return ResponseEntity.ok(version);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", serverName);
        health.put("version", serverVersion);
        return ResponseEntity.ok(health);
    }
}
