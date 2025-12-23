# Textellent MCP Server

A standalone Spring Boot microservice that exposes Textellent's REST APIs through the **Model Context Protocol (MCP)** using JSON-RPC 2.0.

## Overview

This MCP server acts as a bridge between AI agents (like Claude Desktop) and Textellent's existing REST API endpoints. It implements the Model Context Protocol over HTTP, allowing AI agents to discover and execute Textellent API operations as MCP tools.

## Features

- **32 MCP Tools** exposing Textellent's complete API surface
- **JSON-RPC 2.0** compliant MCP implementation
- **JSON Schema Validation** for tool arguments using Everit
- **WebClient-based** HTTP calls to Textellent API
- **Global Exception Handling** with standardized error responses
- **Health Check Endpoint** for monitoring
- **Production-ready** Spring Boot 2.4.5 application

## Project Structure

```
mcp-server/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    └── main/
        ├── java/com/textellent/mcp/
        │   ├── TextellentMcpServerApplication.java
        │   ├── controller/
        │   │   └── McpController.java
        │   ├── registry/
        │   │   ├── McpToolHandler.java
        │   │   └── McpToolRegistry.java
        │   ├── models/
        │   │   ├── McpRpcRequest.java
        │   │   ├── McpRpcResponse.java
        │   │   ├── McpToolDefinition.java
        │   │   └── McpToolCallRequest.java
        │   ├── services/
        │   │   ├── MessageApiService.java
        │   │   ├── ContactApiService.java
        │   │   ├── TagApiService.java
        │   │   ├── AppointmentApiService.java
        │   │   ├── CallbackEventApiService.java
        │   │   └── ConfigurationApiService.java
        │   ├── config/
        │   │   ├── TextellentApiConfig.java
        │   │   └── WebClientConfig.java
        │   └── exception/
        │       └── GlobalExceptionHandler.java
        └── resources/
            ├── application.yml
            └── schemas/
                ├── messages_send.json
                ├── contacts_*.json
                ├── tags_*.json
                ├── appointments_*.json
                ├── events_*.json
                └── webhook_*.json
```

## Prerequisites

- **Java 8** or higher
- **Maven 3.6+**
- **Textellent API Credentials:**
  - `authCode`
  - `partnerClientCode`

## Building the Project

```bash
mvn clean package
```

This creates an executable JAR: `target/textellent-mcp-server-1.0.0.jar`

## Running the Server

### Using Maven

```bash
mvn spring-boot:run
```

### Using JAR

```bash
java -jar target/textellent-mcp-server-1.0.0.jar
```

The server starts on **http://localhost:9090**

## Configuration

### Connecting to Your API Backend

The MCP server needs to know where your Textellent API backend is running. Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 9090  # MCP server port (different from your API backend)

textellent:
  api:
    # Point this to your actual API backend
    base-url: http://localhost:8080  # For local development
    # base-url: https://client.textellent.com  # For production
    timeout: 30000
```

**Important Configuration Notes:**

1. **MCP Server Port** (`server.port: 9090`)
   - This is where the MCP server listens for requests
   - Changed to 9090 to avoid conflicts with your API backend on 8080

2. **API Backend URL** (`textellent.api.base-url`)
   - This is where the MCP server will call your Textellent REST APIs
   - For local development: `http://localhost:8080`
   - For production: `https://client.textellent.com`
   - **The MCP server acts as a proxy/wrapper around your existing APIs**

3. **Authentication Headers**
   - Your API backend should handle `authCode` and `partnerClientCode` headers
   - These are passed through from MCP clients to your backend

## MCP Endpoints

### 1. Health Check

```bash
GET /mcp/health
```

**Example:**
```bash
curl http://localhost:9090/mcp/health
```

**Response:**
```json
{
  "status": "UP",
  "service": "textellent-mcp-server",
  "version": "1.0.0",
  "toolsRegistered": 32
}
```

### 2. MCP Protocol Endpoint

```bash
POST /mcp
```

Handles JSON-RPC 2.0 requests for:
- `tools/list` - List all available tools
- `tools/call` - Execute a specific tool

## Using the MCP Server

### List All Available Tools

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "messages_send",
        "description": "Send SMS/MMS using Textellent API",
        "inputSchema": { ... },
        "outputSchema": { ... }
      },
      {
        "name": "contacts_add",
        "description": "Add new contacts to Textellent",
        "inputSchema": { ... },
        "outputSchema": { ... }
      },
      ...
    ]
  }
}
```

### Call a Tool

#### Example 1: Send a Message

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "messages_send",
      "arguments": {
        "text": "Hello from MCP!",
        "from": "+17607297951",
        "to": "+15109721012",
        "mediaFileIds": [],
        "mediaFileURLs": []
      }
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": {
      "statusCode": "200",
      "message": "Message sent successfully",
      "messageId": "12345"
    },
    "isError": false
  }
}
```

#### Example 2: Add Contacts

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "contacts_add",
      "arguments": {
        "contacts": [
          {
            "contactFirstName": "John",
            "contactLastName": "Doe",
            "phoneMobile": "+15551234567",
            "phoneHome": "",
            "phoneWork": "",
            "phoneAlternate": ""
          }
        ]
      }
    }
  }'
```

#### Example 3: Get All Contacts

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "contacts_get_all",
      "arguments": {
        "searchKey": "John",
        "pageSize": 10,
        "pageNum": 1
      }
    }
  }'
```

#### Example 4: Create Appointment

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "tools/call",
    "params": {
      "name": "appointments_create",
      "arguments": {
        "source": "ZOHO_BOOKING",
        "extId": "RE-00353",
        "appointmentData": {
          "startDateTime": "2025-12-15 14:00:00",
          "endDateTime": "2025-12-15 15:00:00",
          "serviceName": "Tax Preparation",
          "customerFirstName": "Jane",
          "customerLastName": "Smith",
          "customerEmail": "jane@example.com",
          "phoneNumber": "+15551234567",
          "timeZone": "America/New_York",
          "notes": "Initial consultation",
          "status": "upcoming"
        }
      }
    }
  }'
```

#### Example 5: Subscribe to Webhook

**Request:**
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "tools/call",
    "params": {
      "name": "webhook_subscribe",
      "arguments": {
        "target_url": "https://myapp.example.com/webhooks/textellent",
        "event": "INCOMING_MESSAGE"
      }
    }
  }'
```

## Available MCP Tools

### Messages (1 tool)
- `messages_send` - Send SMS/MMS

### Contacts (7 tools)
- `contacts_add` - Add new contacts
- `contacts_update` - Update a contact
- `contacts_get_all` - Get all contacts with search/pagination
- `contacts_get` - Get a specific contact by ID
- `contacts_delete` - Delete a contact
- `contacts_find_multiple_phones` - Find contact by multiple phone numbers
- `contacts_find` - Find contact by extId, phone, or email

### Tags (7 tools)
- `tags_create` - Create contact tags
- `tags_update` - Update a tag
- `tags_get` - Get a specific tag
- `tags_get_all` - Get all tags
- `tags_assign_contacts` - Assign contacts to a tag
- `tags_delete` - Delete a tag
- `tags_remove_contacts` - Remove contacts from a tag

### Appointments (3 tools)
- `appointments_create` - Create an appointment
- `appointments_update` - Update an appointment
- `appointments_cancel` - Cancel an appointment

### Callback Events (11 tools)
- `events_phone_added_wrong_number` - Get wrong number events
- `events_outgoing_delivery_status` - Get delivery status events
- `events_new_contact_details` - Get new contact events
- `events_disassociate_contact_tag` - Get disassociate events
- `events_incoming_message` - Get incoming message events
- `events_phone_added_dnt` - Get DNT add events
- `events_associate_contact_tag` - Get associate events
- `events_appointment_created` - Get appointment created events
- `events_appointment_updated` - Get appointment updated events
- `events_appointment_canceled` - Get appointment canceled events
- `events_phone_removed_dnt` - Get DNT removal events

### Configuration (3 tools)
- `webhook_subscribe` - Subscribe to webhook events
- `webhook_unsubscribe` - Unsubscribe from webhook events
- `webhook_list_subscriptions` - List all webhook subscriptions

## How AI Agents Discover and Call Tools

### Discovery Process

1. **AI Agent Connects** to the MCP server at `http://localhost:9090/mcp`

2. **Agent Calls `tools/list`** to discover available capabilities:
   ```json
   {
     "jsonrpc": "2.0",
     "method": "tools/list",
     "id": 1
   }
   ```

3. **Server Returns Tool Definitions** with schemas:
   - Tool name and description
   - Input schema (JSON Schema format)
   - Output schema (JSON Schema format)

4. **AI Agent Analyzes** user intent and matches it to available tools

5. **Agent Calls `tools/call`** with the appropriate tool and arguments:
   ```json
   {
     "jsonrpc": "2.0",
     "method": "tools/call",
     "id": 2,
     "params": {
       "name": "messages_send",
       "arguments": { ... }
     }
   }
   ```

6. **Server Validates** arguments against the tool's input schema

7. **Server Executes** the tool by calling the Textellent REST API

8. **Server Returns** the result in MCP format

### Example: Claude Desktop Integration

Claude Desktop can connect to this MCP server to give Claude access to Textellent's API operations. When you ask Claude to "send a message to +15551234567", Claude will:

1. Recognize this matches the `messages_send` tool
2. Extract the required parameters
3. Call the MCP server's `tools/call` method
4. Return the result to you

## Error Handling

The server returns JSON-RPC 2.0 error responses:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid params: missing required field 'from'"
  }
}
```

### Error Codes

- `-32700` - Parse error (invalid JSON)
- `-32600` - Invalid request
- `-32601` - Method not found
- `-32602` - Invalid params
- `-32603` - Internal error
- `-32001` - Missing authCode header
- `-32002` - Missing partnerClientCode header

## Architecture

### Tool Registry Pattern

The `McpToolRegistry` maintains a mapping of tool names to handler functions:

```java
registerTool("messages_send", messageApiService::sendMessage);
```

### Service Layer

Each service class (`MessageApiService`, `ContactApiService`, etc.) encapsulates calls to Textellent's REST endpoints using Spring WebClient.

### Schema Validation

Input arguments are validated against JSON schemas loaded from `src/main/resources/schemas/` using the Everit JSON Schema library.

## Development

### Adding a New Tool

1. **Add Service Method** in the appropriate service class
2. **Create JSON Schema** in `src/main/resources/schemas/`
3. **Register Tool** in `McpToolRegistry.registerAllTools()`

### Running Tests

```bash
mvn test
```

## Deployment

### Docker (Optional)

Create a `Dockerfile`:

```dockerfile
FROM openjdk:8-jdk-alpine
COPY target/textellent-mcp-server-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Build and run:

```bash
docker build -t textellent-mcp-server .
docker run -p 8080:8080 textellent-mcp-server
```

## Security Considerations

- **API Credentials**: Never commit `authCode` or `partnerClientCode` to version control
- **Environment Variables**: Use environment variables or external configuration
- **HTTPS**: Deploy behind a reverse proxy with HTTPS in production
- **Rate Limiting**: Consider adding rate limiting for production use

## Troubleshooting

### Server won't start
- Check if port 8080 is available
- Verify Java 8+ is installed: `java -version`

### Tool calls fail
- Verify `authCode` and `partnerClientCode` headers are correct
- Check Textellent API endpoint availability
- Review server logs for detailed error messages

### Schema validation errors
- Ensure all required fields are provided
- Check field types match the schema
- Review the specific tool's JSON schema in `src/main/resources/schemas/`

## License

Copyright © 2025 Textellent. All rights reserved.

## Support

For issues or questions:
- Check server logs in the console output
- Review the Health Check endpoint: `GET /mcp/health`
- Verify Textellent API credentials and permissions

---

**Built with Spring Boot 2.4.5 | Java 8 | Model Context Protocol**
