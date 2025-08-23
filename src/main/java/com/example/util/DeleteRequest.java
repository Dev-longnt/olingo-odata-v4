package com.example.util;

import org.springframework.web.client.RestTemplate;

public class DeleteRequest {
    private final String entitySet;
    private final Object key;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public DeleteRequest(String entitySet, Object key, String baseUrl, RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.key = key;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public void execute() {
        String entityUrl = baseUrl + String.format("%s(%s)", entitySet, key.toString());
        restTemplate.delete(entityUrl);
    }
}
