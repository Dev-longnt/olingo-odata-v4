package com.example.util;

public class UpdateRequest {
    private final String entitySet;
    private final Object key;
    private final String baseUrl;
    private final org.springframework.web.client.RestTemplate restTemplate;

    public UpdateRequest(String entitySet, Object key, String baseUrl, org.springframework.web.client.RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.key = key;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public UpdateRequestWithBody withBody(String body) {
        return new UpdateRequestWithBody(entitySet, key, body, baseUrl, restTemplate);
    }

    public UpdateRequestWithFields withField(String name, Object value) {
        UpdateRequestWithFields builder = new UpdateRequestWithFields(entitySet, key, baseUrl, restTemplate);
        return builder.withField(name, value);
    }
}
