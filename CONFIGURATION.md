# MCP Server Configuration Guide

## Overview

The Textellent MCP Server acts as a **proxy/wrapper** around your existing Textellent API backend. It translates MCP (Model Context Protocol) requests into REST API calls to your backend.

## Architecture

```
AI Agent (Claude Desktop)
         ↓
    MCP Protocol (JSON-RPC 2.0)
         ↓
MCP Server (Port 9090)  ← This project
         ↓
    REST API Calls
         ↓
Your API Backend (Port 8080)  ← Your existing Textellent API
```

## Port Configuration

### MCP Server Port
**Location:** `src/main/resources/application.yml`

```yaml
server:
  port: 9090  # The port where MCP server listens
```

**Purpose:** This is where AI agents (like Claude Desktop) will connect to interact with the MCP protocol.

**Default:** 9090 (changed from 8080 to avoid conflict with your API backend)

### API Backend URL
**Location:** `src/main/resources/application.yml`

```yaml
textellent:
  api:
    base-url: http://localhost:8080  # Your API backend location
    timeout: 30000  # Request timeout in milliseconds
```

**Purpose:** This tells the MCP server where to find your actual Textellent API endpoints.

## Configuration for Different Environments

### Local Development (Default)

```yaml
server:
  port: 9090

textellent:
  api:
    base-url: http://localhost:8080
    timeout: 30000
```

Your local setup:
- **Your API Backend:** Running on `http://localhost:8080`
- **MCP Server:** Running on `http://localhost:9090`
- **AI Agent:** Connects to `http://localhost:9090/mcp`

### Production

```yaml
server:
  port: 9090

textellent:
  api:
    base-url: https://client.textellent.com
    timeout: 30000
```

Production setup:
- **Textellent API:** `https://client.textellent.com`
- **MCP Server:** Running on `http://your-server:9090`
- **AI Agent:** Connects to `http://your-server:9090/mcp`

### Docker/Kubernetes

```yaml
server:
  port: 9090

textellent:
  api:
    base-url: http://textellent-api-service:8080  # Internal service name
    timeout: 30000
```

## How API Calls Are Made

### 1. MCP Request Flow

When an AI agent calls a tool, here's what happens:

```
1. AI Agent sends JSON-RPC request to MCP Server
   POST http://localhost:9090/mcp
   {
     "method": "tools/call",
     "params": {
       "name": "messages_send",
       "arguments": {...}
     }
   }

2. MCP Server validates the request

3. MCP Server looks up the tool in the registry
   Tool: "messages_send" → MessageApiService.sendMessage()

4. Service makes REST call to YOUR API backend
   POST http://localhost:8080/api/v1/messages.json
   Headers:
     - authCode: YOUR_AUTH_CODE
     - partnerClientCode: YOUR_PARTNER_CLIENT_CODE
   Body: {...}

5. Your API backend processes the request

6. MCP Server receives the response

7. MCP Server wraps it in JSON-RPC format

8. AI Agent receives the result
```

### 2. API Endpoint Mapping

All API endpoints are defined in the service classes:

| MCP Tool | Service Method | API Endpoint |
|----------|----------------|--------------|
| messages_send | MessageApiService.sendMessage() | POST /api/v1/messages.json |
| contacts_add | ContactApiService.addContacts() | POST /api/v1/contacts.json |
| contacts_get | ContactApiService.getContact() | GET /api/v1/contacts/{id}.json |
| ... | ... | ... |

**Base URL is prepended automatically:**
- `http://localhost:8080` + `/api/v1/messages.json`
- = `http://localhost:8080/api/v1/messages.json`

### 3. Service Class Example

Here's how `MessageApiService.java` makes the API call:

```java
public Object sendMessage(Map<String, Object> arguments, String authCode, String partnerClientCode) {
    String response = webClient.post()
        .uri("/api/v1/messages.json")  // Appended to base-url
        .header("Content-Type", "application/json")
        .header("authCode", authCode)
        .header("partnerClientCode", partnerClientCode)
        .bodyValue(arguments)
        .retrieve()
        .bodyToMono(String.class)
        .block();

    return response;
}
```

The `webClient` is configured with the `base-url` from `application.yml`.

## Authentication

### How Auth Headers Flow

```
AI Agent Request
  ↓ (includes authCode and partnerClientCode headers)
MCP Server
  ↓ (passes headers through to backend)
Your API Backend
  ↓ (validates credentials)
Response
```

The MCP server **does not** validate credentials. It simply passes them to your backend.

## Changing Configuration

### Option 1: Edit application.yml (Recommended for Development)

```bash
# Edit the file
nano src/main/resources/application.yml

# Change the base-url
textellent:
  api:
    base-url: http://your-api-host:port
```

### Option 2: Environment Variables (Recommended for Production)

```bash
# Set environment variable
export TEXTELLENT_API_BASE_URL=https://client.textellent.com

# Run with Spring Boot property override
java -jar textellent-mcp-server-1.0.0.jar \
  --textellent.api.base-url=${TEXTELLENT_API_BASE_URL}
```

### Option 3: External Configuration File

Create `application-local.yml`:

```yaml
textellent:
  api:
    base-url: http://192.168.1.100:8080
```

Run with profile:

```bash
java -jar textellent-mcp-server-1.0.0.jar --spring.profiles.active=local
```

## Verifying Configuration

### 1. Check MCP Server is Running

```bash
curl http://localhost:9090/mcp/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "textellent-mcp-server",
  "version": "1.0.0",
  "toolsRegistered": 32
}
```

### 2. Test API Backend Connection

Check the logs when MCP server starts. You should see:

```
Textellent MCP Server Started!
MCP Endpoint: http://localhost:9090/mcp
Health Check: http://localhost:9090/mcp/health
```

### 3. Test a Tool Call

```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "contacts_get_all",
      "arguments": {
        "pageSize": 1,
        "pageNum": 1
      }
    }
  }'
```

If this works, your configuration is correct!

## Common Issues

### Issue: Connection refused to localhost:8080

**Problem:** Your API backend is not running.

**Solution:** Start your Textellent API backend first:
```bash
# Make sure your API is running on port 8080
curl http://localhost:8080/api/v1/health  # or whatever your health endpoint is
```

### Issue: Port 9090 already in use

**Problem:** Another application is using port 9090.

**Solution:** Change the MCP server port in `application.yml`:
```yaml
server:
  port: 9091  # or any available port
```

### Issue: 401 Unauthorized from backend

**Problem:** Invalid or missing credentials.

**Solution:** Check that you're passing valid `authCode` and `partnerClientCode` headers:
```bash
curl -X POST http://localhost:9090/mcp \
  -H "authCode: YOUR_VALID_AUTH_CODE" \
  -H "partnerClientCode: YOUR_VALID_CLIENT_CODE" \
  ...
```

## Summary

- **MCP Server Port:** 9090 (configurable in `application.yml`)
- **API Backend URL:** http://localhost:8080 (configurable in `application.yml`)
- **Purpose:** MCP server wraps your existing REST APIs with MCP protocol
- **No modification needed:** Your API backend remains unchanged
- **Authentication:** Headers are passed through from MCP clients to your backend

For more details, see the main [README.md](README.md).
