package com.bcfinancial.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Serves static files from the {@code web/} classpath directory.
 * Registered at {@code /} in {@link com.bcfinancial.Main}; the more-specific
 * {@code /api} context takes priority for all API calls.
 */
public class StaticHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(StaticHandler.class.getName());

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=UTF-8",
            "css",  "text/css; charset=UTF-8",
            "js",   "application/javascript; charset=UTF-8",
            "png",  "image/png",
            "ico",  "image/x-icon"
    );

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // Map "/" → "index.html"
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        String resource = "web" + path;
        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);

        if (is == null) {
            // Fall back to index.html for client-side routing
            is = getClass().getClassLoader().getResourceAsStream("web/index.html");
            if (is == null) {
                byte[] body = ("Not found: " + path).getBytes();
                ex.sendResponseHeaders(404, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
        }

        String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "html";
        String contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        ex.getResponseHeaders().set("Content-Type", contentType);

        byte[] bytes = is.readAllBytes();
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
