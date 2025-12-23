#!/usr/bin/env node

/**
 * MCP HTTP Bridge for Claude Desktop
 *
 * This bridge allows Claude Desktop (which expects stdio communication)
 * to connect to your HTTP-based MCP server.
 */

const http = require('http');
const readline = require('readline');

// Configuration - using hardcoded value as fallback since claude_desktop_config.json keeps getting cleared
const AUTH_CODE = process.env.TEXTELLENT_AUTH_CODE || 'aEukfTq6hiERxHErYZxN5J7oGGH6tZ';
const PARTNER_CLIENT_CODE = process.env.TEXTELLENT_PARTNER_CLIENT_CODE || '';
const MCP_SERVER_HOST = process.env.MCP_SERVER_HOST || 'localhost';
const MCP_SERVER_PORT = process.env.MCP_SERVER_PORT || '9090';
const MCP_SERVER_PATH = process.env.MCP_SERVER_PATH || '/mcp';

// Log startup info to stderr (will appear in Claude Desktop logs)
console.error(`[Bridge] Starting MCP HTTP Bridge`);
console.error(`[Bridge] Server: http://${MCP_SERVER_HOST}:${MCP_SERVER_PORT}${MCP_SERVER_PATH}`);
console.error(`[Bridge] Auth configured: ${AUTH_CODE ? 'Yes' : 'No'}`);
console.error(`[Bridge] Partner code configured: ${PARTNER_CLIENT_CODE ? 'Yes' : 'No'}`);

// Create readline interface for stdio communication
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

// Handle JSON-RPC requests from stdin
rl.on('line', (line) => {
  if (!line.trim()) return;

  try {
    const request = JSON.parse(line);
    console.error(`[Bridge] Received request: ${request.method} (id: ${request.id})`);

    // Forward request to HTTP MCP server
    const options = {
      hostname: MCP_SERVER_HOST,
      port: MCP_SERVER_PORT,
      path: MCP_SERVER_PATH,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'authCode': AUTH_CODE,
        'partnerClientCode': PARTNER_CLIENT_CODE
      }
    };

    const req = http.request(options, (res) => {
      let data = '';

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        // Write response to stdout for Claude Desktop
        try {
          const response = JSON.parse(data);
          console.error(`[Bridge] Sending response for ${request.method} (id: ${request.id})`);
          console.log(JSON.stringify(response));
        } catch (err) {
          console.error(`[Bridge] Error parsing response: ${err.message}`);
          console.log(JSON.stringify({
            jsonrpc: '2.0',
            id: request.id,
            error: {
              code: -32603,
              message: 'Invalid response from MCP server',
              data: data
            }
          }));
        }
      });
    });

    req.on('error', (error) => {
      console.log(JSON.stringify({
        jsonrpc: '2.0',
        id: request.id,
        error: {
          code: -32603,
          message: `Connection error: ${error.message}`,
          data: {
            host: MCP_SERVER_HOST,
            port: MCP_SERVER_PORT,
            path: MCP_SERVER_PATH
          }
        }
      }));
    });

    req.write(JSON.stringify(request));
    req.end();

  } catch (error) {
    console.log(JSON.stringify({
      jsonrpc: '2.0',
      id: null,
      error: {
        code: -32700,
        message: `Parse error: ${error.message}`
      }
    }));
  }
});

// Handle process termination
process.on('SIGINT', () => {
  rl.close();
  process.exit(0);
});

process.on('SIGTERM', () => {
  rl.close();
  process.exit(0);
});
