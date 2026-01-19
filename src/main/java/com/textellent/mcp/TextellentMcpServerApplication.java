package com.textellent.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Main application class for Textellent MCP Server.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class TextellentMcpServerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TextellentMcpServerApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "9090");
        String contextPath = env.getProperty("server.servlet.context-path", "/");

        System.out.println("\n========================================");
        System.out.println("Textellent MCP Server Started!");
        System.out.println("MCP Endpoint: http://localhost:" + port + contextPath + "mcp");
        System.out.println("Health Check: http://localhost:" + port + contextPath + "mcp/health");
        System.out.println("========================================\n");
    }
}
