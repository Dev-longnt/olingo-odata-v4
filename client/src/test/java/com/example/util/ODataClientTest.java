package com.example.util;

import com.example.util.ODataQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ODataClientTest {

    private ODataClient odataClient;
    private String baseUrl = "http://localhost:8080/odata/";

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ODataQueryBuilder oDataQueryBuilder;

    @BeforeEach
    void setUp() {
        odataClient = new ODataClient(baseUrl, restTemplate, oDataQueryBuilder);
    }

    @Test
    void testGetCollection() throws URISyntaxException {
        String entitySet = "Products";
        String expectedUrl = baseUrl + entitySet;
        URI expectedUri = new URI(expectedUrl);
        ResponseEntity<String> expectedResponse = new ResponseEntity<>("[]", HttpStatus.OK);

        // Mock the ODataQueryBuilder behavior
        when(oDataQueryBuilder.buildUri(expectedUrl)).thenReturn(expectedUri);

        // Mock the RestTemplate behavior
        when(restTemplate.getForEntity(eq(expectedUri), eq(String.class)))
                .thenReturn(expectedResponse);

        // Call the method under test
        ResponseEntity<String> actualResponse = odataClient.get(entitySet).execute();

        // Verify interactions and assertions
        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).getForEntity(eq(expectedUri), eq(String.class));
    }

    @Test
    void testGetSingleEntity() throws URISyntaxException {
        String entitySet = "Products";
        int key = 1;
        String expectedUrl = baseUrl + String.format("%s(%s)", entitySet, key);
        URI expectedUri = new URI(expectedUrl);
        ResponseEntity<String> expectedResponse = new ResponseEntity<>("{}", HttpStatus.OK);

        // Mock the ODataQueryBuilder behavior
        when(oDataQueryBuilder.buildUri(expectedUrl)).thenReturn(expectedUri);

        // Mock the RestTemplate behavior
        when(restTemplate.getForEntity(eq(expectedUri), eq(String.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<String> actualResponse = odataClient.get(entitySet, key).execute();

        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).getForEntity(eq(expectedUri), eq(String.class));
    }

    @Test
    void testCreateEntityWithBody() {
        String entitySet = "Products";
        String jsonBody = "{\"Name\":\"New Product\"}";
        ResponseEntity<String> expectedResponse = new ResponseEntity<>("{}", HttpStatus.CREATED);

        when(restTemplate.postForEntity(eq(baseUrl + entitySet), any(HttpEntity.class), eq(String.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<String> actualResponse = odataClient.create(entitySet).withBody(jsonBody).execute();

        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).postForEntity(eq(baseUrl + entitySet), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testCreateEntityWithFields() {
        String entitySet = "Products";
        ResponseEntity<String> expectedResponse = new ResponseEntity<>("{}", HttpStatus.CREATED);

        when(restTemplate.postForEntity(eq(baseUrl + entitySet), any(HttpEntity.class), eq(String.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<String> actualResponse = odataClient.create(entitySet)
                .withField("Name", "New Product")
                .withField("Price", 100.0)
                .execute();

        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).postForEntity(eq(baseUrl + entitySet), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testUpdateEntityWithBody() throws URISyntaxException {
        String entitySet = "Products";
        int key = 1;
        String jsonBody = "{\"Name\":\"Updated Product\"}";
        String expectedUrl = baseUrl + String.format("%s(%s)", entitySet, key);
        ResponseEntity<String> expectedResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);

        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<String> actualResponse = odataClient.update(entitySet, key).withBody(jsonBody).execute();

        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testUpdateEntityWithFields() throws URISyntaxException {
        String entitySet = "Products";
        int key = 1;
        ResponseEntity<String> expectedResponse = new ResponseEntity<>(HttpStatus.NO_CONTENT);
        String expectedUrl = baseUrl + String.format("%s(%s)", entitySet, key);

        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<String> actualResponse = odataClient.update(entitySet, key)
                .withField("Name", "Updated Product")
                .withField("Price", 200.0)
                .execute();

        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testDeleteEntity() throws URISyntaxException {
        String entitySet = "Products";
        int key = 1;
        String expectedUrl = baseUrl + String.format("%s(%s)", entitySet, key);

        // Mock the RestTemplate behavior for delete
        // Mockito.doNothing() is used for void methods
        // when(restTemplate.delete(eq(expectedUrl))).thenReturn(null); // This is wrong for void

        odataClient.delete(entitySet, key).execute();

        verify(restTemplate).delete(eq(expectedUrl));
    }
}
