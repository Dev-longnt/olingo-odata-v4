package com.example.util;

import com.example.util.ODataQueryBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

public class GetSingleRequest {
    private final String entitySet;
    private final Object key;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ODataQueryBuilder oDataQueryBuilder;

    public GetSingleRequest(String entitySet, Object key, String baseUrl, RestTemplate restTemplate, ODataQueryBuilder oDataQueryBuilder) {
        this.entitySet = entitySet;
        this.key = key;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.oDataQueryBuilder = oDataQueryBuilder;
    }

    public ResponseEntity<String> execute() throws URISyntaxException {
        String entityUrl = baseUrl + String.format("%s(%s)", entitySet, key.toString());
        URI uri = oDataQueryBuilder.buildUri(entityUrl);
        return restTemplate.getForEntity(uri, String.class);
    }
}

