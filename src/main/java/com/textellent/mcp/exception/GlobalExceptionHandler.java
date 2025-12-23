package com.textellent.mcp.exception;

import com.textellent.mcp.models.McpRpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the MCP server.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle JSON parse errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<McpRpcResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.error("JSON parse error", ex);

        McpRpcResponse.McpRpcError error = new McpRpcResponse.McpRpcError(
                -32700,
                "Parse error: Invalid JSON"
        );

        McpRpcResponse response = new McpRpcResponse(null, error);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Handle type mismatch errors.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<McpRpcResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        logger.error("Type mismatch error", ex);

        McpRpcResponse.McpRpcError error = new McpRpcResponse.McpRpcError(
                -32602,
                "Invalid params: " + ex.getMessage()
        );

        McpRpcResponse response = new McpRpcResponse(null, error);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Handle illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<McpRpcResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Illegal argument", ex);

        McpRpcResponse.McpRpcError error = new McpRpcResponse.McpRpcError(
                -32602,
                "Invalid params: " + ex.getMessage()
        );

        McpRpcResponse response = new McpRpcResponse(null, error);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<McpRpcResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);

        McpRpcResponse.McpRpcError error = new McpRpcResponse.McpRpcError(
                -32603,
                "Internal error: " + ex.getMessage()
        );

        McpRpcResponse response = new McpRpcResponse(null, error);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
