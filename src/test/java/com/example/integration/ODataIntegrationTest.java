package com.example.integration;

import com.example.ODataServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import com.example.util.DbUnitTestUtils;

import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.Assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ODataIntegrationTest {
    // Utility methods moved to DbUnitTestUtils

    private static Server server;
    private static String BASE_URL = "http://localhost:8080/ODataServlet/";
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
        // Ensure tables exist before DBUnit operations
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS PRODUCTS (ID INT PRIMARY KEY, Name VARCHAR(255), Description VARCHAR(255), Price DOUBLE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS CATEGORIES (ID INT PRIMARY KEY, Name VARCHAR(255))");
        }
        InputStream is = ODataIntegrationTest.class.getClassLoader().getResourceAsStream("dataset.xml");
        IDataSet dataSet = new FlatXmlDataSetBuilder().build(is);
        DatabaseOperation.CLEAN_INSERT.execute(dbUnitConnection, dataSet);

        // Setup Jetty Server
        server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(new ODataServlet());
        context.addServlet(servletHolder, "/ODataServlet/*");

        server.start();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clean and insert data using DBUnit
        InputStream is = getClass().getClassLoader().getResourceAsStream("dataset.xml");
        IDataSet dataSet = new FlatXmlDataSetBuilder().build(is);
        DatabaseOperation.CLEAN_INSERT.execute(dbUnitConnection, dataSet);

        // Restart Jetty server to reload servlet after DBUnit setup
        if (server != null && server.isRunning()) {
            server.stop();
            server.join();
        }
        server.start();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        // Shutdown Jetty Server
        server.stop();
        server.join();

        // Close H2 Database Connection
        if (h2Connection != null) {
            h2Connection.close();
        }
    }

    @Test
    void testReadEntityCollection() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String url = new com.example.ODataQueryBuilder().build(BASE_URL + "Products");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Products"));
        assertTrue(response.body().contains("Notebook"));

        org.json.JSONObject root = new org.json.JSONObject(response.body());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductsTable = DbUnitTestUtils.buildTableFromJson(productsJson, dbProductsTable);
        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, null, null);

        Assertion.assertEquals(dbFilteredTable, apiProductsTable);
    }

    @Test
    void testReadSingleEntity() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String url = new com.example.ODataQueryBuilder().build(BASE_URL + "Products(1)");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Notebook"));
        assertTrue(response.body().contains("\"Price\":"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.body());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }
    @Test
    void testCreateEntity() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String json = "{\"ID\":11,\"Name\":\"Camera\",\"Description\":\"Digital camera\",\"Price\":400.00}";
        String url = new com.example.ODataQueryBuilder().build(BASE_URL + "Products");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("Camera"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(response.body());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 11, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testUpdateEntity() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String json = "{\"Name\":\"Notebook Pro\",\"Price\":1500.00}";
        String url = new com.example.ODataQueryBuilder().build(BASE_URL + "Products(1)");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());

        String getUrl = new com.example.ODataQueryBuilder().build(BASE_URL + "Products(1)");
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(getUrl))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertTrue(getResponse.body().contains("Notebook Pro"));
        assertTrue(getResponse.body().contains("1500"));

        org.json.JSONObject productJson = DbUnitTestUtils.parseProductJson(getResponse.body());
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductTable = DbUnitTestUtils.buildTableFromJsonObject(productJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(dbProductsTable, row -> row[0] != null && Integer.parseInt(row[0].toString()) == 1, null);

        Assertion.assertEquals(dbFilteredTable, apiProductTable);
    }

    @Test
    void testDeleteEntity() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String url = new com.example.ODataQueryBuilder().build(BASE_URL + "Products(2)");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());

        // Verify deletion by GET and compare API vs DB
        String getUrl = new com.example.ODataQueryBuilder().build(BASE_URL + "Products(2)");
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(getUrl))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getResponse.statusCode());

        // DBUnit validation: Tablet should not exist in DB
        ITable tabletTable = dbUnitConnection.createQueryTable("tablet_check",
            "SELECT ID, Name, Description, Price FROM PRODUCTS WHERE Name = 'Tablet'");
        assertEquals(0, tabletTable.getRowCount(), "Tablet should be deleted from DB");
    }

    @Test
    void testFilterAndOrderBy() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String url = new com.example.ODataQueryBuilder()
            .filter("Price gt 500")
            .orderBy("Price desc")
            .build(BASE_URL + "Products");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Notebook"));
        assertTrue(response.body().contains("Smartphone"));

        org.json.JSONObject root = new org.json.JSONObject(response.body());
        org.json.JSONArray productsJson = root.getJSONArray("value");
        ITable dbProductsTable = dbUnitConnection.createDataSet().getTable("PRODUCTS");
        DefaultTable apiProductsTable = DbUnitTestUtils.buildTableFromJson(productsJson, dbProductsTable);

        DefaultTable dbFilteredTable = DbUnitTestUtils.filterDbTable(
            dbProductsTable,
            row -> row[3] != null && ((Double)row[3]) > 500,
            (a, b) -> Double.compare((Double)b[3], (Double)a[3])
        );

        Assertion.assertEquals(dbFilteredTable, apiProductsTable);
    }
}