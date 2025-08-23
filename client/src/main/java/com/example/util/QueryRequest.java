package com.example.util;

import com.example.util.ODataQueryBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

public class QueryRequest {
    private final ODataQueryBuilder oDataQueryBuilder;
    private final String entitySet;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public QueryRequest(String entitySet, String baseUrl, RestTemplate restTemplate, ODataQueryBuilder oDataQueryBuilder) {
        this.entitySet = entitySet;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.oDataQueryBuilder = oDataQueryBuilder;
    }

    public QueryRequest filter(String filter) {
        oDataQueryBuilder.filter(filter);
        return this;
    }

    public QueryRequest orderBy(String orderBy) {
        oDataQueryBuilder.orderBy(orderBy);
        return this;
    }

    public QueryRequest expand(String expandExpression) {
        oDataQueryBuilder.expand(expandExpression);
        return this;
    }

    public QueryRequest count() {
        oDataQueryBuilder.count();
        return this;
    }

    public ResponseEntity<String> execute() throws URISyntaxException {
        URI uri = oDataQueryBuilder.buildUri(baseUrl + entitySet);
        return restTemplate.getForEntity(uri, String.class);
    }
}