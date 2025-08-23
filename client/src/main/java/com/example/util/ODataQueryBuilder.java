package com.example.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ODataQueryBuilder {

    private final Map<String, String> queryOptions = new LinkedHashMap<>();

    public ODataQueryBuilder filter(String filterExpression) {
        queryOptions.put("$filter", filterExpression);
        return this;
    }

    public ODataQueryBuilder orderBy(String orderByExpression) {
        queryOptions.put("$orderby", orderByExpression);
        return this;
    }

    public ODataQueryBuilder select(String selectFields) {
        queryOptions.put("$select", selectFields);
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

    public ODataQueryBuilder expand(String expandExpression) {
        queryOptions.put("$expand", expandExpression);
        return this;
    }

    public ODataQueryBuilder count() {
        queryOptions.put("$count", "true");
        return this;
    }

    public String build(String baseUrl) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!queryOptions.isEmpty()) {
            sb.append("?");
            queryOptions.forEach((key, value) -> {
                try {
                    String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20");
                    sb.append(key).append("=").append(encodedValue).append("&");
                } catch (Exception e) {
                    throw new RuntimeException("Failed to encode OData value", e);
                }
            });
            sb.setLength(sb.length() - 1); // Remove trailing '&'
        }
        return sb.toString();
    }

    public URI buildUri(String baseUrl) throws URISyntaxException {
        return new URI(build(baseUrl));
    }
}
