package com.textellent.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application class for Textellent MCP Server.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class TextellentMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TextellentMcpServerApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Textellent MCP Server Started!");
        System.out.println("MCP Endpoint: http://localhost:9090/mcp");
        System.out.println("Health Check: http://localhost:9090/mcp/health");
        System.out.println("========================================\n");
    }
}
