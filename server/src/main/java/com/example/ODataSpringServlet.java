package com.example;

import org.apache.olingo.server.api.ODataHttpHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;

@Component
public class ODataSpringServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ODataSpringServlet.class);
    private final ODataHttpHandler handler;

    @Autowired
    public ODataSpringServlet(ODataHttpHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        // Wrap request to allow reading body multiple times
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(req);

        // Log detailed request information
        logRequestDetails(wrappedRequest);

        try {
            handler.process(wrappedRequest, resp);

            long endTime = System.currentTimeMillis();
            logger.info("=== SALESFORCE ODATA REQUEST COMPLETED ===");
            logger.info("Response Status: {}", resp.getStatus());
            logger.info("Processing Time: {}ms", (endTime - startTime));
            logger.info("===========================================");

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            logger.error("=== SALESFORCE ODATA REQUEST FAILED ===");
            logger.error("Error: {}", e.getMessage(), e);
            logger.error("Processing Time: {}ms", (endTime - startTime));
            logger.error("=======================================");
            throw e;
        }
    }

    private void logRequestDetails(HttpServletRequest req) {
        try {
            logger.info("========== SALESFORCE ODATA REQUEST ==========");
            logger.info("Timestamp: {}", new java.util.Date());
            logger.info("Method: {}", req.getMethod());
            logger.info("Request URL: {}", req.getRequestURL().toString());
            logger.info("Query String: {}", req.getQueryString());
            logger.info("Context Path: {}", req.getContextPath());
            logger.info("Servlet Path: {}", req.getServletPath());
            logger.info("Path Info: {}", req.getPathInfo());
            logger.info("Remote Address: {}", req.getRemoteAddr());
            logger.info("Remote Host: {}", req.getRemoteHost());

            // Log all headers
            logger.info("--- REQUEST HEADERS ---");
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = req.getHeader(headerName);
                logger.info("Header: {} = {}", headerName, headerValue);
            }

            // Log all parameters
            logger.info("--- REQUEST PARAMETERS ---");
            Enumeration<String> paramNames = req.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = paramNames.nextElement();
                String[] paramValues = req.getParameterValues(paramName);
                logger.info("Parameter: {} = {}", paramName, String.join(", ", paramValues));
            }

            // Log request body if it exists (for POST/PUT requests)
            if ("POST".equalsIgnoreCase(req.getMethod()) || "PUT".equalsIgnoreCase(req.getMethod())
                    || "PATCH".equalsIgnoreCase(req.getMethod())) {
                if (req instanceof CachedBodyHttpServletRequest) {
                    CachedBodyHttpServletRequest cachedRequest = (CachedBodyHttpServletRequest) req;
                    String body = cachedRequest.getBody();
                    if (!body.isEmpty()) {
                        logger.info("--- REQUEST BODY ---");
                        logger.info("Body: {}", body);
                    }
                }
            }

            logger.info("=============================================");

        } catch (Exception e) {
            logger.error("Error logging request details: {}", e.getMessage(), e);
        }
    }

    /**
     * Wrapper class to cache request body so it can be read multiple times
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;
        private String bodyString;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);

            // Read and cache the request body
            try (ServletInputStream inputStream = request.getInputStream()) {
                this.cachedBody = inputStream.readAllBytes();
                this.bodyString = new String(this.cachedBody, StandardCharsets.UTF_8);
            }
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
        }

        public String getBody() {
            return this.bodyString;
        }
    }

    /**
     * Custom ServletInputStream implementation for cached body
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream byteArrayInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return byteArrayInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Not implemented for this use case
        }

        @Override
        public int read() throws IOException {
            return byteArrayInputStream.read();
        }
    }
}