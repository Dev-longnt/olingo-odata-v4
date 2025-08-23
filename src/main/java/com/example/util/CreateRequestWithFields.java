package com.example.util;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public class CreateRequestWithFields {
    private final String entitySet;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public CreateRequestWithFields(String entitySet, String baseUrl, RestTemplate restTemplate) {
        this.entitySet = entitySet;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    public CreateRequestWithFields withField(String name, Object value) {
        this.fields.put(name, value);
        return this;
    }

    public ResponseEntity<String> execute() {
        String jsonBody = new JSONObject(fields).toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        return restTemplate.postForEntity(baseUrl + entitySet, request, String.class);
    }
}
