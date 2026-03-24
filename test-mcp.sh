#!/bin/bash

# Test script for Textellent Appointments MCP Server
# Usage: ./test-mcp.sh YOUR_AUTH_CODE YOUR_CLIENT_CODE

AUTH_CODE="${1:-test_auth_code}"
PARTNER_CLIENT_CODE="${2:-test_client_code}"
MCP_URL="http://localhost:9090/mcp"

echo "======================================"
echo "Testing Textellent Appointments MCP Server"
echo "======================================"
echo ""

# Test 1: Health Check
echo "Test 1: Health Check"
echo "-------------------------------------"
curl -s http://localhost:9090/mcp/health | jq .
echo ""
echo ""

# Test 2: List All Tools
echo "Test 2: List All Tools"
echo "-------------------------------------"
curl -s -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -H "authCode: $AUTH_CODE" \
  -H "partnerClientCode: $PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }' | jq '.result.tools | length'
echo "tools found"
echo ""
echo ""

# Test 3: Get tool details
echo "Test 3: Get appointments_create Tool Details"
echo "-------------------------------------"
curl -s -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -H "authCode: $AUTH_CODE" \
  -H "partnerClientCode: $PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }' | jq '.result.tools[] | select(.name == "appointments_create")'
echo ""
echo ""

# Test 4: Validate a plan via dsl_execute_plan (dry run; no Textellent API calls)
echo "Test 4: dsl_execute_plan dry run"
echo "-------------------------------------"
curl -s -X POST "$MCP_URL" \
  -H "Content-Type: application/json" \
  -H "authCode: $AUTH_CODE" \
  -H "partnerClientCode: $PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "dsl_execute_plan",
      "arguments": {
        "dryRun": true,
        "plan": {
          "version": "1.0",
          "simplePlan": {
            "description": "smoke test",
            "permissions": {
              "mode": "read_only",
              "allowedServers": ["textellent"],
              "allowedTools": ["events_appointment_created"]
            },
            "limits": { "maxToolCalls": 5, "maxDurationMs": 60000 },
            "steps": [
              {
                "id": "s1",
                "server": "textellent",
                "tool": "events_appointment_created",
                "args": { "limit": 1 }
              }
            ]
          }
        }
      }
    }
  }' | jq .
echo ""
echo ""

echo "======================================"
echo "Testing Complete!"
echo "======================================"
echo ""
echo "If all tests passed, your MCP server is working correctly!"
echo ""
echo "Next steps:"
echo "1. Make sure your API backend is running on port 8080"
echo "2. Use valid authCode and partnerClientCode"
echo "3. Connect Claude Desktop using the mcp-bridge.js script"
echo ""
