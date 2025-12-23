# Testing MCP Server with AI Agents

## Overview

Your MCP server runs over HTTP (JSON-RPC 2.0), which means AI agents can connect to it via HTTP requests. Here are several ways to test it:

---

## Option 1: Using MCP Inspector (Recommended for Testing)

The MCP Inspector is an official tool for testing MCP servers.

### Installation

```bash
npm install -g @modelcontextprotocol/inspector
```

### Usage

Since your server uses HTTP, you can test it directly:

```bash
# The inspector will connect to your HTTP endpoint
mcp-inspector http://localhost:9090/mcp
```

You'll need to set the auth headers. Create a wrapper script:

**`test-mcp.js`:**
```javascript
const http = require('http');

const authCode = 'YOUR_AUTH_CODE';
const partnerClientCode = 'YOUR_PARTNER_CLIENT_CODE';

const options = {
  hostname: 'localhost',
  port: 9090,
  path: '/mcp',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'authCode': authCode,
    'partnerClientCode': partnerClientCode
  }
};

const request = {
  jsonrpc: '2.0',
  id: 1,
  method: 'tools/list',
  params: {}
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => console.log(JSON.stringify(JSON.parse(data), null, 2)));
});

req.on('error', (error) => console.error('Error:', error));
req.write(JSON.stringify(request));
req.end();
```

Run it:
```bash
node test-mcp.js
```

---

## Option 2: Using Claude Desktop with HTTP Bridge

Claude Desktop expects MCP servers over stdio, but we can create a bridge.

### Create an MCP HTTP Bridge

**`mcp-bridge.js`:**
```javascript
#!/usr/bin/env node

const http = require('http');
const readline = require('readline');

const AUTH_CODE = process.env.TEXTELLENT_AUTH_CODE || 'YOUR_AUTH_CODE';
const PARTNER_CLIENT_CODE = process.env.TEXTELLENT_PARTNER_CLIENT_CODE || 'YOUR_CLIENT_CODE';
const MCP_SERVER_URL = 'http://localhost:9090/mcp';

// Read JSON-RPC from stdin
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  try {
    const request = JSON.parse(line);

    const options = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'authCode': AUTH_CODE,
        'partnerClientCode': PARTNER_CLIENT_CODE
      }
    };

    const req = http.request(MCP_SERVER_URL, options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        // Write response to stdout
        console.log(data);
      });
    });

    req.on('error', (error) => {
      const errorResponse = {
        jsonrpc: '2.0',
        id: request.id,
        error: {
          code: -32603,
          message: error.message
        }
      };
      console.log(JSON.stringify(errorResponse));
    });

    req.write(JSON.stringify(request));
    req.end();
  } catch (error) {
    console.error('Parse error:', error);
  }
});
```

Make it executable:
```bash
chmod +x mcp-bridge.js
```

### Configure Claude Desktop

Edit your Claude Desktop config file:

**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Linux:** `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "textellent": {
      "command": "node",
      "args": ["/path/to/your/mcp-bridge.js"],
      "env": {
        "TEXTELLENT_AUTH_CODE": "your_auth_code_here",
        "TEXTELLENT_PARTNER_CLIENT_CODE": "your_client_code_here"
      }
    }
  }
}
```

### Restart Claude Desktop

After saving the config, restart Claude Desktop. You should see "Textellent" in the MCP servers list.

---

## Option 3: Using Postman/Insomnia (Manual Testing)

### Import into Postman

1. Create a new request
2. Set method to `POST`
3. URL: `http://localhost:9090/mcp`
4. Headers:
   - `Content-Type`: `application/json`
   - `authCode`: `YOUR_AUTH_CODE`
   - `partnerClientCode`: `YOUR_CLIENT_CODE`

### Test Requests

**List All Tools:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

**Send a Message:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "messages_send",
    "arguments": {
      "text": "Hello from Postman!",
      "from": "+17607297951",
      "to": "+15109721012",
      "mediaFileIds": [],
      "mediaFileURLs": []
    }
  }
}
```

**Get All Contacts:**
```json
{
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
}
```

---

## Option 4: Using curl (Command Line Testing)

### Test Health Check
```bash
curl http://localhost:9090/mcp/health
```

### List All Tools
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }' | jq .
```

### Call a Tool
```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "contacts_get_all",
      "arguments": {
        "pageSize": 5,
        "pageNum": 1
      }
    }
  }' | jq .
```

---

## Option 5: Python Test Client

Create a simple Python client:

**`test_mcp.py`:**
```python
import requests
import json

class MCPClient:
    def __init__(self, base_url, auth_code, partner_client_code):
        self.base_url = base_url
        self.headers = {
            'Content-Type': 'application/json',
            'authCode': auth_code,
            'partnerClientCode': partner_client_code
        }
        self.request_id = 0

    def _send_request(self, method, params=None):
        self.request_id += 1
        request = {
            'jsonrpc': '2.0',
            'id': self.request_id,
            'method': method,
            'params': params or {}
        }

        response = requests.post(
            self.base_url,
            headers=self.headers,
            json=request
        )
        return response.json()

    def list_tools(self):
        return self._send_request('tools/list')

    def call_tool(self, tool_name, arguments):
        return self._send_request('tools/call', {
            'name': tool_name,
            'arguments': arguments
        })

# Usage
if __name__ == '__main__':
    client = MCPClient(
        'http://localhost:9090/mcp',
        'YOUR_AUTH_CODE',
        'YOUR_CLIENT_CODE'
    )

    # List all tools
    print("Available tools:")
    tools = client.list_tools()
    for tool in tools['result']['tools']:
        print(f"  - {tool['name']}: {tool['description']}")

    # Call a tool
    print("\nCalling contacts_get_all:")
    result = client.call_tool('contacts_get_all', {
        'pageSize': 5,
        'pageNum': 1
    })
    print(json.dumps(result, indent=2))
```

Run it:
```bash
python test_mcp.py
```

---

## Option 6: JavaScript/Node.js Test Client

**`test_mcp_client.js`:**
```javascript
const axios = require('axios');

class MCPClient {
  constructor(baseUrl, authCode, partnerClientCode) {
    this.baseUrl = baseUrl;
    this.headers = {
      'Content-Type': 'application/json',
      'authCode': authCode,
      'partnerClientCode': partnerClientCode
    };
    this.requestId = 0;
  }

  async sendRequest(method, params = {}) {
    this.requestId++;
    const request = {
      jsonrpc: '2.0',
      id: this.requestId,
      method: method,
      params: params
    };

    const response = await axios.post(this.baseUrl, request, {
      headers: this.headers
    });
    return response.data;
  }

  async listTools() {
    return this.sendRequest('tools/list');
  }

  async callTool(toolName, arguments) {
    return this.sendRequest('tools/call', {
      name: toolName,
      arguments: arguments
    });
  }
}

// Usage
(async () => {
  const client = new MCPClient(
    'http://localhost:9090/mcp',
    'YOUR_AUTH_CODE',
    'YOUR_CLIENT_CODE'
  );

  // List all tools
  console.log('Available tools:');
  const tools = await client.listTools();
  tools.result.tools.forEach(tool => {
    console.log(`  - ${tool.name}: ${tool.description}`);
  });

  // Call a tool
  console.log('\nCalling messages_send:');
  const result = await client.callTool('messages_send', {
    text: 'Hello from Node.js MCP client!',
    from: '+17607297951',
    to: '+15109721012',
    mediaFileIds: [],
    mediaFileURLs: []
  });
  console.log(JSON.stringify(result, null, 2));
})();
```

Install dependencies and run:
```bash
npm install axios
node test_mcp_client.js
```

---

## What You Can Ask Claude Desktop (Once Connected)

Once Claude Desktop is connected to your MCP server, you can ask it natural language questions like:

- "List all my contacts"
- "Send a message to +15109721012 saying 'Hello!'"
- "Create a new contact for John Doe with phone +15551234567"
- "Get all tags"
- "Create an appointment for tomorrow at 2pm"
- "Show me incoming message events"
- "Subscribe to incoming message webhooks at https://myapp.com/webhook"

Claude will automatically:
1. Understand your intent
2. Match it to the appropriate MCP tool
3. Extract the required parameters
4. Call the tool via your MCP server
5. Your MCP server forwards it to your API backend
6. Return the result to Claude
7. Claude presents it to you in natural language

---

## Troubleshooting

### MCP Server Not Responding
```bash
# Check if server is running
curl http://localhost:9090/mcp/health

# Check server logs
# (Look at the terminal where you ran mvn spring-boot:run)
```

### Authentication Errors
- Make sure `authCode` and `partnerClientCode` headers are set correctly
- Check that your API backend accepts these credentials

### Connection Refused
- Make sure your API backend is running on port 8080
- Check the `base-url` in `application.yml`

### Tool Call Fails
- Check the MCP server logs for error details
- Verify the tool arguments match the schema
- Make sure your API backend is accessible

---

## Next Steps

1. **Choose a testing method** (Claude Desktop with bridge is most powerful)
2. **Set your credentials** in the configuration
3. **Start making requests** to test your tools
4. **Monitor the logs** to see requests flowing through

Your MCP server is production-ready and can be integrated with any AI agent that supports the Model Context Protocol!
