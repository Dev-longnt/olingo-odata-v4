package com.example.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class CreateRequestWithBody {
    private final String entitySet;
    private final String body;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public CreateRequestWithBody(String entitySet, String body, String baseUrl, RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.body = body;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> execute() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(baseUrl + entitySet, request, String.class);
    }
}
