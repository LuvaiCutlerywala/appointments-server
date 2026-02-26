# Textellent MCP Server

A production-ready Spring Boot microservice that exposes Textellent's REST APIs through the **Model Context Protocol (MCP)** using JSON-RPC 2.0 over HTTPS.

Designed for deployment as a **public, remote MCP connector** compatible with Claude Connectors, ChatGPT Apps (MCP), n8n, and other MCP-compatible platforms.

## Features

### Core Capabilities
- **32 MCP Tools** exposing Textellent's complete API surface
- **MCP Protocol 2025-06-18** compliant implementation
- **Streamable HTTP Transport** with JSON-RPC 2.0
- **SSE Support** for streaming events (optional)
- **JSON Schema Validation** for all tool arguments

### Security & Authorization
- **OAuth2 Resource Server** with JWT validation
- **Scope-based Access Control**:
  - `textellent.read` - Read-only operations
  - `textellent.write` - Write/mutating operations
- **API Key Authentication** (alternative mode)
- **Multi-tenant Isolation** via JWT claims
- **CORS Configuration** for web clients

### Operational Excellence
- **Rate Limiting** (separate limits for read/write operations)
- **Circuit Breaker & Retries** for resilience
- **Structured Logging** with correlation IDs
- **Audit Logging** for all tool calls
- **Health Checks** and metrics (Spring Actuator)
- **Prometheus Metrics** for monitoring

### Tool Safety Metadata
- **Read-Only Hints** for non-mutating tools
- **Destructive Hints** for dangerous operations
- **Required Scope** annotations per tool
- **Tool Categorization** in listings

## Quick Start

### Local Development

```bash
# Clone and configure
git clone <repo-url>
cd mcp-server
cp .env.example .env

# Edit .env for local mode
echo "SECURITY_MODE=local" >> .env
echo "SPRING_PROFILES_ACTIVE=local" >> .env

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run

# Test
curl http://localhost:9090/health
curl http://localhost:9090/version
```

### Docker Deployment

```bash
# Build image
docker build -t textellent-mcp-server:latest .

# Run with docker-compose
cp .env.example .env
# Edit .env with production settings
docker-compose up -d

# View logs
docker-compose logs -f
```

### Production Deployment

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for comprehensive guides on:
- Kubernetes deployment
- Cloud platforms (AWS, GCP, Azure)
- OAuth2 configuration
- Connector setup for Claude/ChatGPT/n8n
- Monitoring and troubleshooting

## Architecture

### Deployment Architecture

```
┌─────────────────────────────────────────┐
│        AI Platforms                     │
│   - Claude Connectors                   │
│   - ChatGPT Apps                        │
│   - n8n MCP Client                      │
│   - Custom MCP Clients                  │
└──────────────┬──────────────────────────┘
               │
               │ HTTPS (MCP over HTTP)
               │ OAuth2 JWT / API Key
               │
               ▼
┌──────────────────────────────────────────┐
│     Load Balancer / API Gateway          │
│   - TLS Termination                      │
│   - DDoS Protection                      │
│   - Rate Limiting (L7)                   │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│   Textellent MCP Server (Instances)      │
│                                          │
│   ┌────────────────────────────────┐    │
│   │  Security Layer                │    │
│   │  - OAuth2 JWT Validation       │    │
│   │  - Scope Enforcement           │    │
│   │  - Tenant Isolation            │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  MCP Controller                │    │
│   │  - JSON-RPC Router             │    │
│   │  - Rate Limiter                │    │
│   │  - Audit Logger                │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  Tool Registry                 │    │
│   │  - 32 Tool Definitions         │    │
│   │  - Safety Metadata             │    │
│   │  - Schema Validation           │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  Resilience Layer              │    │
│   │  - Circuit Breaker             │    │
│   │  - Retry Logic                 │    │
│   │  - Timeout Management          │    │
│   └────────────┬───────────────────┘    │
└────────────────┼────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│     Textellent API Backend               │
│   (Your existing REST API)               │
└──────────────────────────────────────────┘
```

### Request Flow

```
1. Client Request
   ├─→ MCP-Protocol-Version header check
   ├─→ OAuth2 JWT validation
   └─→ Extract tenant context from JWT

2. Authorization
   ├─→ Check required scope for tool
   ├─→ Enforce read vs write permissions
   └─→ Rate limit check (tenant-specific)

3. Tool Execution
   ├─→ Validate arguments against JSON schema
   ├─→ Execute tool via registry
   ├─→ Apply circuit breaker & retries
   └─→ Call Textellent backend API

4. Response
   ├─→ Format as MCP-compliant content
   ├─→ Log audit event
   └─→ Return JSON-RPC response
```

## API Endpoints

### MCP Protocol Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/mcp` | Required | Main MCP JSON-RPC endpoint |
| GET | `/mcp/sse` | Required | SSE event stream (optional) |
| GET | `/health` | Public | Health check |
| GET | `/version` | Public | Version information |
| GET | `/actuator/health` | Auth | Detailed health |
| GET | `/actuator/metrics` | Auth | Metrics |
| GET | `/actuator/prometheus` | Auth | Prometheus metrics |

### MCP Methods

| Method | Description | Auth Required |
|--------|-------------|---------------|
| `initialize` | Protocol handshake | No |
| `tools/list` | List available tools | Yes (read scope) |
| `tools/call` | Execute a tool | Yes (tool-specific) |
| `notifications/*` | Client notifications | No |

## Available Tools (32 Total)

### Messages (1)
- `messages_send` - Send SMS/MMS (**write**, destructive:false)

### Contacts (7)
- `contacts_add` - Add contacts (**write**, destructive:false)
- `contacts_update` - Update contact (**write**, destructive:false)
- `contacts_get_all` - List contacts (**read**, readonly:true)
- `contacts_get` - Get contact details (**read**, readonly:true)
- `contacts_delete` - Delete contact (**write**, destructive:true)
- `contacts_find_multiple_phones` - Find by phones (**read**, readonly:true)
- `contacts_find` - Find contact (**read**, readonly:true)

### Tags (7)
- `tags_create` - Create tag (**write**, destructive:false)
- `tags_update` - Update tag (**write**, destructive:false)
- `tags_get` - Get tag details (**read**, readonly:true)
- `tags_get_all` - List all tags (**read**, readonly:true)
- `tags_assign_contacts` - Assign contacts to tag (**write**, destructive:false)
- `tags_delete` - Delete tag (**write**, destructive:true)
- `tags_remove_contacts` - Remove contacts from tag (**write**, destructive:true)

### Appointments (3)
- `appointments_create` - Create appointment (**write**, destructive:false)
- `appointments_update` - Update appointment (**write**, destructive:false)
- `appointments_cancel` - Cancel appointment (**write**, destructive:true)

### Events (11) - All **read-only**
- `events_incoming_message` - Get incoming messages
- `events_outgoing_delivery_status` - Get delivery status
- `events_new_contact_details` - Get new contacts
- `events_phone_added_wrong_number` - Get wrong number events
- `events_phone_added_dnt` - Get DNT additions
- `events_phone_removed_dnt` - Get DNT removals
- `events_associate_contact_tag` - Get tag associations
- `events_disassociate_contact_tag` - Get tag disassociations
- `events_appointment_created` - Get appointment creations
- `events_appointment_updated` - Get appointment updates
- `events_appointment_canceled` - Get appointment cancellations

### Configuration (3)
- `webhook_subscribe` - Subscribe to webhooks (**write**, destructive:false)
- `webhook_unsubscribe` - Unsubscribe (**write**, destructive:true)
- `webhook_list_subscriptions` - List subscriptions (**read**, readonly:true)

## Security Configuration

### OAuth2 JWT Mode (Production)

```yaml
SECURITY_MODE=oauth2
OAUTH2_ISSUER_URI=https://your-auth-provider.com/
# OR
OAUTH2_JWK_SET_URI=https://your-auth-provider.com/.well-known/jwks.json
```

**JWT Requirements**:
- Standard claims: `iss`, `sub`, `exp`, `iat`
- Tenant claim: `tenant_id`, `tenantId`, or `organization_id`
- Scope claim: `scope` (space-separated) or `scp` (array)

**Example JWT**:
```json
{
  "iss": "https://auth.yourcompany.com",
  "sub": "user|123456",
  "exp": 1735689600,
  "iat": 1735686000,
  "tenant_id": "acme-corp",
  "scope": "textellent.read textellent.write"
}
```

### API Key Mode (Simple)

```yaml
SECURITY_MODE=apikey
API_KEY=your-secret-key-here
API_KEY_SCOPES=textellent.read,textellent.write
```

**Client Request**:
```bash
curl -X POST https://your-server.com/mcp \
  -H "X-API-Key: your-secret-key-here" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### Local Mode (Development Only)

```yaml
SECURITY_MODE=local
SPRING_PROFILES_ACTIVE=local
```

**⚠️ Warning**: Disables all security. Never use in production!

## Usage Examples

### Initialize Connection

```bash
curl -X POST https://mcp.yourcompany.com/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {}
  }'
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "tools": {}
    },
    "serverInfo": {
      "name": "textellent-mcp-server",
      "version": "1.0.0"
    }
  }
}
```

### List Available Tools

```bash
curl -X POST https://mcp.yourcompany.com/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_TEXTELLENT_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

Response includes categorized tools:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [...],
    "categorized": {
      "readOnly": [...],
      "write": [...]
    },
    "totalCount": 32
  }
}
```

### Call a Read-Only Tool

```bash
curl -X POST https://mcp.yourcompany.com/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_TEXTELLENT_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "contacts_get_all",
      "arguments": {
        "searchKey": "",
        "pageSize": 10,
        "pageNum": 1
      }
    }
  }'
```

### Call a Write Tool

```bash
curl -X POST https://mcp.yourcompany.com/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_TEXTELLENT_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
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

## Environment Variables

### Required

| Variable | Description | Example |
|----------|-------------|---------|
| `SECURITY_MODE` | Auth mode: oauth2, apikey, local | `oauth2` |
| `TEXTELLENT_API_BASE_URL` | Backend API URL | `https://client.textellent.com` |

### OAuth2 Configuration

| Variable | Description |
|----------|-------------|
| `OAUTH2_ISSUER_URI` | OAuth2 issuer URI (auto-discovers JWKS) |
| `OAUTH2_JWK_SET_URI` | Direct JWKS endpoint URL |

### API Key Configuration

| Variable | Description |
|----------|-------------|
| `API_KEY` | Secret API key |
| `API_KEY_SCOPES` | Comma-separated scopes |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `9090` | Server port |
| `ALLOWED_ORIGINS` | `*` | CORS allowed origins |
| `RATELIMIT_READ_CAPACITY` | `100` | Read ops/minute |
| `RATELIMIT_WRITE_CAPACITY` | `20` | Write ops/minute |
| `LOG_LEVEL` | `INFO` | Root log level |
| `LOG_LEVEL_SECURITY` | `INFO` | Security log level |

See `.env.example` for complete list.

## Multi-Tenancy

The server automatically extracts tenant context from JWT claims:

1. **Tenant Identification**:
   - `tenant_id` claim (preferred)
   - `tenantId` claim (alternative)
   - `organization_id` claim (alternative)

2. **Tenant Isolation**:
   - Separate rate limit buckets per tenant
   - Tenant ID included in all audit logs
   - Tenant context in structured logging (MDC)

3. **Header Override** (API key mode):
   ```bash
   -H "X-Tenant-ID: custom-tenant-id"
   ```

## Rate Limiting

### Configuration

```yaml
# Read operations (GET, LIST)
RATELIMIT_READ_CAPACITY=100         # Max tokens
RATELIMIT_READ_REFILL=100           # Tokens per refill
RATELIMIT_READ_DURATION=1           # Refill interval (minutes)

# Write operations (CREATE, UPDATE, DELETE)
RATELIMIT_WRITE_CAPACITY=200
RATELIMIT_WRITE_REFILL=200
RATELIMIT_WRITE_DURATION=1
```

### Behavior

- **Per-Tenant**: Each tenant has separate buckets
- **Tool-Based**: Read tools check read limit, write tools check write limit
- **Response**: HTTP 200 with JSON-RPC error `-32000` when exceeded

## Audit Logging

All tool calls are logged with:
- Timestamp
- Tenant ID
- User ID
- Trace ID (correlation)
- Tool name
- Status (SUCCESS/FAILURE)
- Redacted arguments (sensitive fields masked)

Example audit log:
```json
{
  "timestamp": "2025-01-15T10:30:45.123Z",
  "event": "TOOL_CALL",
  "tenantId": "acme-corp",
  "userId": "user|123456",
  "traceId": "abc-def-123",
  "toolName": "messages_send",
  "status": "SUCCESS",
  "arguments": {
    "text": "Hello",
    "from": "+1234567890",
    "authCode": "***REDACTED***"
  }
}
```

## Monitoring

### Health Checks

```bash
# Basic health
curl https://mcp.yourcompany.com/health

# Detailed health (requires auth)
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/health
```

### Metrics

```bash
# All metrics
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/metrics

# Prometheus format
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/prometheus
```

### Key Metrics

- `http.server.requests` - Request counts and latencies
- `resilience4j.circuitbreaker.calls` - Circuit breaker stats
- `resilience4j.retry.calls` - Retry attempts
- `jvm.memory.used` - Memory usage
- `system.cpu.usage` - CPU usage

## Development

### Prerequisites

- Java 8+
- Maven 3.6+
- Docker (optional)

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

### Run Locally

```bash
# With Maven
mvn spring-boot:run

# With JAR
java -jar target/textellent-mcp-server-1.0.0.jar
```

### IDE Setup

1. Import as Maven project
2. Enable Lombok annotation processing
3. Set Java SDK to 8 or higher

## Deployment

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for detailed guides on:

- **Docker** - Container deployment
- **Kubernetes** - Scalable cluster deployment
- **AWS ECS/Fargate** - Serverless containers
- **Google Cloud Run** - Managed containers
- **Azure Container Instances** - Quick deployment

## Connector Integration

### Claude Connectors

Submit to Claude Directory with:
- Endpoint: `https://your-server.com/mcp`
- Protocol: MCP over HTTPS
- Auth: OAuth2 with PKCE
- Scopes: `textellent.read`, `textellent.write`

### ChatGPT Apps (MCP)

Configure GPT Action with OpenAPI spec pointing to `/mcp` endpoint.

### n8n MCP Client

Use HTTP Request node with:
- POST to `/mcp`
- OAuth2 authentication
- JSON-RPC 2.0 request body

See [DEPLOYMENT.md](DEPLOYMENT.md#connector-setup) for detailed setup instructions.

## Troubleshooting

### Common Issues

**401 Unauthorized**
- Check JWT token validity and expiration
- Verify issuer URI matches token issuer

**403 Forbidden**
- User lacks required scope
- Check `requiredScope` in tool definition

**429 Rate Limit Exceeded**
- Adjust rate limits or implement backoff
- Check tenant-specific limits

**Connection Refused**
- Verify `TEXTELLENT_API_BASE_URL`
- Ensure backend API is accessible

See [DEPLOYMENT.md](DEPLOYMENT.md#troubleshooting) for more details.

## License

Copyright © 2025 Textellent. All rights reserved.

## Support

- **Documentation**: [DEPLOYMENT.md](DEPLOYMENT.md), [CONFIGURATION.md](CONFIGURATION.md)
- **Logs**: Check structured logs with trace IDs
- **Health**: Monitor `/actuator/health` endpoint
- **Metrics**: View `/actuator/prometheus` for insights

---

**Version**: 1.0.0
**MCP Protocol**: 2025-06-18
**Spring Boot**: 2.4.5
**Java**: 8+
