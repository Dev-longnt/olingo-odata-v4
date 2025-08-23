package com.example.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class UpdateRequestWithBody {
    private final String entitySet;
    private final Object key;
    private final String body;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public UpdateRequestWithBody(String entitySet, Object key, String body, String baseUrl, RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.key = key;
        this.body = body;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> execute() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        String entityUrl = baseUrl + String.format("%s(%s)", entitySet, key.toString());
        return restTemplate.exchange(entityUrl, HttpMethod.PUT, request, String.class);
    }
}
