package com.example;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ODataQueryBuilder {

    private final Map<String, String> queryOptions = new LinkedHashMap<>();

    public ODataQueryBuilder filter(String filterExpression) {
        queryOptions.put("$filter", encode(filterExpression));
        return this;
    }

    public ODataQueryBuilder orderBy(String orderByExpression) {
        queryOptions.put("$orderby", encode(orderByExpression));
        return this;
    }

    public ODataQueryBuilder select(String selectFields) {
        queryOptions.put("$select", encode(selectFields));
        return this;
    }

    public ODataQueryBuilder top(int count) {
        queryOptions.put("$top", String.valueOf(count));
        return this;
    }

    public ODataQueryBuilder skip(int count) {
        queryOptions.put("$skip", String.valueOf(count));
        return this;
    }

    public String build(String baseUrl) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!queryOptions.isEmpty()) {
            sb.append("?");
            queryOptions.forEach((key, value) -> sb.append(key).append("=").append(value).append("&"));
            sb.setLength(sb.length() - 1); // Remove trailing '&'
        }
        return sb.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode OData value", e);
        }
    }
}