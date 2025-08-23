package com.example.util;

import com.example.ODataQueryBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ODataClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ODataQueryBuilder oDataQueryBuilder;

    public ODataClient(String baseUrl, RestTemplate restTemplate, ODataQueryBuilder oDataQueryBuilder) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.oDataQueryBuilder = oDataQueryBuilder;
    }

    public QueryRequest get(String entitySet) {
        return new QueryRequest(entitySet, this.baseUrl, this.restTemplate, this.oDataQueryBuilder);
    }

    public GetSingleRequest get(String entitySet, Object key) {
        return new GetSingleRequest(entitySet, key, this.baseUrl, this.restTemplate, this.oDataQueryBuilder);
    }

    public CreateRequest create(String entitySet) {
        return new CreateRequest(entitySet, this.baseUrl, this.restTemplate);
    }

    public UpdateRequest update(String entitySet, Object key) {
        return new UpdateRequest(entitySet, key, this.baseUrl, this.restTemplate);
    }
    
    public DeleteRequest delete(String entitySet, Object key) {
        return new DeleteRequest(entitySet, key, this.baseUrl, this.restTemplate);
    }

    private String buildEntityUrl(String entitySet, Object key) {
        return baseUrl + String.format("%s(%s)", entitySet, key.toString());
    }

    private HttpEntity<String> createJsonHttpEntity(String jsonPayload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(jsonPayload, headers);
    }
}
