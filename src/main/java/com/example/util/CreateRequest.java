package com.example.util;

public class CreateRequest {
    private final String entitySet;
    private final String baseUrl;
    private final org.springframework.web.client.RestTemplate restTemplate;

    public CreateRequest(String entitySet, String baseUrl, org.springframework.web.client.RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public CreateRequestWithBody withBody(String body) {
        return new CreateRequestWithBody(entitySet, body, baseUrl, restTemplate);
    }

    public CreateRequestWithFields withField(String name, Object value) {
        CreateRequestWithFields builder = new CreateRequestWithFields(entitySet, baseUrl, restTemplate);
        return builder.withField(name, value);
    }
}
