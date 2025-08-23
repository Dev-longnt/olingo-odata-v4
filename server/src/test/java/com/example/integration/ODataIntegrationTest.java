package com.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.dbunit.Assertion;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.OdataApplication;
import com.example.util.DbUnitTestUtils;
import com.example.util.ODataClient;

@SpringBootTest(classes = OdataApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ODataIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String BASE_URL;
    private ODataClient odataClient;
    private static Connection h2Connection;
    private static IDatabaseConnection dbUnitConnection;

    @BeforeAll
    static void setUpAll() throws Exception {
        // Delete existing H2 database files to ensure a clean start
        File dbFile = new File("./testdb.mv.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        File traceFile = new File("./testdb.trace.db");
        if (traceFile.exists()) {
            traceFile.delete();
        }

        // Setup H2 Database
        Class.forName("org.h2.Driver");
        h2Connection = DriverManager.getConnection("jdbc:h2:file:./testdb;DB_CLOSE_DELAY=-1", "sa", "");
        dbUnitConnection = new DatabaseConnection(h2Connection, "PUBLIC"); // Explicitly set schema

        // Create tables
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS PRODUCTS (ID INT PRIMARY KEY, Name VARCHAR(255), Description VARCHAR(255), Price DOUBLE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS CATEGORIES (ID INT PRIMARY KEY, Name VARCHAR(255))");
        }
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        // Close H2 Database Connection
        if (h2Connection != null) {
            h2Connection.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        BASE_URL = "http://localhost:" + port + "/odata/";
        odataClient = new ODataClient(BASE_URL, restTemplate.getRestTemplate(), new com.example.ODataQueryBuilder());
        // Clean and insert data using DBUnit
        InputStream is = getClass().getClassLoader().getResourceAsStream("dataset.xml");
        IDataSet dataSet = new FlatXmlDataSetBuilder().build(is);
        DatabaseOperation.CLEAN_INSERT.execute(dbUnitConnection, dataSet);
    }

    private ResponseEntity<String> executeJsonRequest(String url, HttpMethod method, String jsonPayload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
        return restTemplate.exchange(url, method, request, String.class);
    }

    @Test
    void testReadEntityCollection() throws Exception {
        URI uri = new com.example.ODataQueryBuilder().buildUri(BASE_URL + "Products");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Products"));
        assertTrue(response.getBody().contains("Notebook"));

        org.json.JSONObject root = new org.json.JSONObject(response.getBody());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductsTable = DbUnitTestUtils.buildTableFromJson(productsJson, dbProductsTable);
        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, null, null);

        Assertion.assertEquals(dbFilteredTable, apiProductsTable);
    }

    @Test
    void testReadSingleEntity() throws Exception {
        URI uri = new com.example.ODataQueryBuilder().buildUri(BASE_URL + "Products(1)");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Notebook"));
        assertTrue(response.getBody().contains("\"Price\":"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testCreateEntity() throws Exception {
        String json = "{\"ID\":11,\"Name\":\"Camera\",\"Description\":\"Digital camera\",\"Price\":400.00}";
        ResponseEntity<String> response = executeJsonRequest(BASE_URL + "Products", HttpMethod.POST, json);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("Camera"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 11, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testUpdateEntity() throws Exception {
        String json = "{\"Name\":\"Notebook Pro\",\"Price\":1500.00}";
        ResponseEntity<String> response = executeJsonRequest(BASE_URL + "Products(1)", HttpMethod.PUT, json);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        URI getUri = new com.example.ODataQueryBuilder().buildUri(BASE_URL + "Products(1)");
        ResponseEntity<String> getResponse = restTemplate.getForEntity(getUri, String.class);
        assertTrue(getResponse.getBody().contains("Notebook Pro"));
        assertTrue(getResponse.getBody().contains("1500"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(getResponse.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testDeleteEntity() throws Exception {
        odataClient.delete("Products", 2).execute();

        URI getUri = new com.example.ODataQueryBuilder().buildUri(BASE_URL + "Products(2)");
        ResponseEntity<String> getResponse = restTemplate.getForEntity(getUri, String.class);
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());

        // DBUnit validation: Tablet should not exist in DB
        ITable tabletTable = dbUnitConnection.createQueryTable("tablet_check",
            "SELECT ID, Name, Description, Price FROM PRODUCTS WHERE Name = 'Tablet'");
        assertEquals(0, tabletTable.getRowCount(), "Tablet should be deleted from DB");
    }

    @Test
    void testFilterAndOrderBy() throws Exception {
        ResponseEntity<String> response = odataClient.get("Products")
            .filter("Price gt 500")
            .orderBy("Price desc")
            .execute();

        System.out.println("Response Status Code: " + response.getStatusCode());
        System.out.println("Response Body: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Products"));
        assertTrue(response.getBody().contains("Notebook"));

        org.json.JSONObject root = new org.json.JSONObject(response.getBody());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductsTable = DbUnitTestUtils.buildTableFromJson(productsJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(
            dbProductsTable,
            row -> row[3] != null && ((Double)row[3]) > 500,
            (a, b) -> Double.compare((Double)b[3], (Double)a[3])
        );

        System.out.println("\n--- API Products Table ---");
        DbUnitTestUtils.printTable(apiProductsTable);
        System.out.println("\n--- DB Filtered Table ---");
        DbUnitTestUtils.printTable(dbFilteredTable);

        Assertion.assertEquals(dbFilteredTable, apiProductsTable);
    }
}
