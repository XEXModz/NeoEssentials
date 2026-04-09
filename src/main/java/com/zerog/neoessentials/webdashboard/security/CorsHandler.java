package com.zerog.neoessentials.webdashboard.security;

import com.sun.net.httpserver.HttpExchange;
import com.zerog.neoessentials.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized CORS (Cross-Origin Resource Sharing) handler.
 * 
 * Reads the allowed origin from config instead of hardcoding "*".
 * When enableCORS is true, uses the dashboard URL as the allowed origin.
 * When enableCORS is false, no CORS headers are set (most secure).
 * 
 * Usage in any handler:
 *   CorsHandler.apply(exchange);
 *   CorsHandler.applyWithMethods(exchange, "GET, POST, OPTIONS");
 *   if (CorsHandler.handlePreflight(exchange)) return;
 */
public class CorsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorsHandler.class);
    
    // Default methods and headers if not specified
    private static final String DEFAULT_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String DEFAULT_HEADERS = "Content-Type, Authorization";
    
    private CorsHandler() {
        // Utility class — no instantiation
    }
    
    /**
     * Get the allowed origin from config.
     * Returns the dashboard URL (e.g. "http://localhost:8080") instead of "*".
     * Returns null if CORS is disabled.
     */
    public static String getAllowedOrigin() {
        try {
            ConfigManager config = ConfigManager.getInstance();
            if (config == null) {
                return null;
            }
            
            if (!config.isCorsEnabled()) {
                return null;
            }
            
            // Use the configured allowed origin if set, otherwise use the dashboard URL
            String allowedOrigin = config.getCorsAllowedOrigin();
            if (allowedOrigin != null && !allowedOrigin.isEmpty()) {
                return allowedOrigin;
            }
            
            // Fall back to the dashboard's own URL
            return config.getWebDashboardUrl();
        } catch (Exception e) {
            LOGGER.debug("Could not determine CORS origin, defaulting to dashboard URL: {}", e.getMessage());
            return "http://localhost:8080";
        }
    }
    
    /**
     * Apply CORS headers to a response with default methods and headers.
     */
    public static void apply(HttpExchange exchange) {
        applyWithMethods(exchange, DEFAULT_METHODS);
    }
    
    /**
     * Apply CORS headers to a response with specific allowed methods.
     */
    public static void applyWithMethods(HttpExchange exchange, String methods) {
        applyFull(exchange, methods, DEFAULT_HEADERS);
    }
    
    /**
     * Apply CORS headers to a response with specific methods and headers.
     */
    public static void applyFull(HttpExchange exchange, String methods, String headers) {
        String origin = getAllowedOrigin();
        if (origin == null) {
            // CORS disabled — don't set any CORS headers
            return;
        }
        
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        
        if (methods != null && !methods.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", methods);
        }
        
        if (headers != null && !headers.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", headers);
        }
    }
    
    /**
     * Handle an OPTIONS preflight request.
     * Returns true if this was a preflight request and was handled (caller should return).
     * Returns false if this is not an OPTIONS request (caller should continue).
     */
    public static boolean handlePreflight(HttpExchange exchange) {
        if (!"OPTIONS".equals(exchange.getRequestMethod())) {
            return false;
        }
        
        try {
            applyFull(exchange, DEFAULT_METHODS, DEFAULT_HEADERS);
            exchange.sendResponseHeaders(204, -1);
        } catch (Exception e) {
            LOGGER.debug("Error handling CORS preflight: {}", e.getMessage());
        }
        
        return true;
    }
}
