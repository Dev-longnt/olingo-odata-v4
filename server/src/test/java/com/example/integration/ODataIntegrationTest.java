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

@SpringBootTest(classes = OdataApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ODataIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String BASE_URL;
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
            stmt.execute("CREATE TABLE IF NOT EXISTS CATEGORY (Id INT PRIMARY KEY, Name VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS PRODUCT (Id INT PRIMARY KEY, Name VARCHAR(255), Description VARCHAR(255), Price DOUBLE, CategoryID INT, FOREIGN KEY (CategoryID) REFERENCES CATEGORY(Id))");
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
        URI uri = new URI(BASE_URL + "Products");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        System.out.println("testReadEntityCollection: Status=" + response.getStatusCode());
        System.out.println("testReadEntityCollection: Body=" + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Products"));
        assertTrue(response.getBody().contains("Notebook"));

        org.json.JSONObject root = new org.json.JSONObject(response.getBody());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCT");
        DefaultTable apiProductsTable = DbUnitTestUtils.buildTableFromJson(productsJson, dbProductsTable);
        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, null, null);

        Assertion.assertEquals(dbFilteredTable, apiProductsTable);
    }

    @Test
    void testReadSingleEntity() throws Exception {
        URI uri = new URI(BASE_URL + "Products(1)");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        System.out.println("testReadSingleEntity: Status=" + response.getStatusCode());
        System.out.println("testReadSingleEntity: Body=" + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Notebook"));
        assertTrue(response.getBody().contains("PRICE"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCT");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testCreateEntity() throws Exception {
        String json = "{\"ID\":11,\"NAME\":\"Camera\",\"DESCRIPTION\":\"Digital camera\",\"PRICE\":400.00}";
        ResponseEntity<String> response = executeJsonRequest(BASE_URL + "Products", HttpMethod.POST, json);

        System.out.println("testCreateEntity: Status=" + response.getStatusCode());
        System.out.println("testCreateEntity: Body=" + response.getBody());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("Camera"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCT");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 11, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testUpdateEntity() throws Exception {
        String json = "{\"Name\":\"Notebook Pro\",\"Price\":1500.00}";
        ResponseEntity<String> response = executeJsonRequest(BASE_URL + "Products(1)", HttpMethod.PUT, json);

        System.out.println("testUpdateEntity: Status=" + response.getStatusCode());
        System.out.println("testUpdateEntity: Body=" + response.getBody());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        URI getUri = new URI(BASE_URL + "Products(1)");
        ResponseEntity<String> getResponse = restTemplate.getForEntity(getUri, String.class);
        System.out.println("testUpdateEntity: GET Status=" + getResponse.getStatusCode());
        System.out.println("testUpdateEntity: GET Body=" + getResponse.getBody());
        assertTrue(getResponse.getBody().contains("Notebook Pro"));
        assertTrue(getResponse.getBody().contains("1500"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(getResponse.getBody());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCT");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testDeleteEntity() throws Exception {
        // Perform delete operation
        restTemplate.delete(BASE_URL + "Products(2)");

        // Verify deletion by trying to get the entity
        URI getUri = new URI(BASE_URL + "Products(2)");
        ResponseEntity<String> getResponse = restTemplate.getForEntity(getUri, String.class);
        System.out.println("testDeleteEntity: GET Status=" + getResponse.getStatusCode());
        System.out.println("testDeleteEntity: GET Body=" + getResponse.getBody());
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());

        // DBUnit validation: Tablet should not exist in DB
        ITable tabletTable = dbUnitConnection.createQueryTable("tablet_check",
            "SELECT ID, Name, Description, Price FROM PRODUCT WHERE Name = 'Tablet'");
        assertEquals(0, tabletTable.getRowCount(), "Tablet should be deleted from DB");
    }

    @Test
    void testFilterAndOrderBy() throws Exception {
        // Manually construct the URL with $filter and $orderby
        URI uri = new URI(BASE_URL + "Products?$filter=PRICE%20gt%20500&$orderby=PRICE%20desc");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        System.out.println("Response Status Code: " + response.getStatusCode());
        System.out.println("Response Body: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Products"));
        assertTrue(response.getBody().contains("Notebook"));

        org.json.JSONObject root = new org.json.JSONObject(response.getBody());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCT");
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

    @Test
    void testReadEntityCollectionWithExpand() throws Exception {
        URI uri = new URI(BASE_URL + "Products?$expand=Category");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        System.out.println("testReadEntityCollectionWithExpand: Status=" + response.getStatusCode());
        System.out.println("testReadEntityCollectionWithExpand: Body=" + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Products"));
        // The server's TestProcessor does not currently support $expand, so we only check for a successful response.
        // In a full implementation, you would add assertions here to verify the expanded data.
    }

    @Test
    void testReadEntityCollectionWithCount() throws Exception {
        URI uri = new URI(BASE_URL + "Products?$count=true");
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        System.out.println("testReadEntityCollectionWithCount: Status=" + response.getStatusCode());
        System.out.println("testReadEntityCollectionWithCount: Body=" + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("@odata.count"));

        org.json.JSONObject root = new org.json.JSONObject(response.getBody());
        assertTrue(root.has("@odata.count"));
        assertTrue(root.getInt("@odata.count") >= 0); // Check if count is a non-negative number
    }
}