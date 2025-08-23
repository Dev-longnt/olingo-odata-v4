package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseHelper {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHelper.class);

    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_CONNECTION_URL = "jdbc:h2:file:./testdb";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            logger.error("H2 Driver not found: {}", e.getMessage(), e);
        }
        return DriverManager.getConnection(DB_CONNECTION_URL, DB_USER, DB_PASSWORD);
    }

    public static void createTable() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS PRODUCTS (ID INT PRIMARY KEY, Name VARCHAR(255), Description VARCHAR(255), Price DOUBLE)");
            stmt.execute("MERGE INTO PRODUCTS KEY(ID) VALUES (1, 'Notebook', 'A notebook computer', 1200.00)");
            stmt.execute("MERGE INTO PRODUCTS KEY(ID) VALUES (2, 'Monitor', 'A computer monitor', 300.00)");
            logger.info("PRODUCTS table created/merged successfully.");
        } catch (SQLException e) {
            logger.error("Error creating/merging PRODUCTS table: {}", e.getMessage(), e);
            throw e;
        }
    }
}
