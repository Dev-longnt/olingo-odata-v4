# Project Architecture Document: Olingo OData V4 Test Application

## 1. Overview

This project implements a simple OData V4 service using Apache Olingo, backed by an in-memory H2 database. It demonstrates basic CRUD (Create, Read, Update, Delete) operations for a "Product" entity. The application is packaged as a Java web application (WAR) and can be deployed in any compatible servlet container.

## 2. Technology Stack

*   **Language**: Java 21
*   **Build Tool**: Gradle
*   **OData Framework**: Apache Olingo OData V4 (odata-server-core, odata-commons-api, etc.)
*   **Database**: H2 Database (in-memory/file-based for testing)
*   **Web Server**: Standard Java Servlet Container (e.g., Tomcat, Jetty - Jetty used for testing)
*   **JSON Processing**: `org.json` library
*   **Logging**: SLF4J with Logback (though `System.out.println` is currently used in some places)
*   **Testing**: JUnit Jupiter, Mockito, DBUnit

## 3. Core Components

The application's architecture is structured around the OData specification and Apache Olingo's framework.

### 3.1. `ODataServlet` (`src/main/java/com/example/ODataServlet.java`)

*   **Role**: The main entry point for all OData requests. It extends `javax.servlet.http.HttpServlet`.
*   **Functionality**:
    *   Initializes the OData service by creating an `OData` instance.
    *   Registers the `TestEdmProvider` to define the service's metadata (Entity Data Model).
    *   Registers `TestProcessor` as both `EntityCollectionProcessor` and `EntityProcessor` to handle data operations.
    *   Delegates incoming HTTP requests to the Olingo `ODataHttpHandler` for processing.
*   **Configuration**: Mapped to the `/odata/*` URL pattern in `WEB-INF/web.xml`.

### 3.2. `TestEdmProvider` (`src/main/java/com/example/TestEdmProvider.java`)

*   **Role**: Defines the Entity Data Model (EDM) for the OData service. It extends `CsdlAbstractEdmProvider`.
*   **Functionality**:
    *   Declares the `Product` Entity Type with properties:
        *   `ID` (Int32, Key)
        *   `Name` (String)
        *   `Description` (String)
        *   `Price` (Double)
    *   Defines the `Products` Entity Set, which is a collection of `Product` entities.
    *   Establishes the `Container` that holds the `Products` Entity Set.
*   **Namespace**: `OData.Test`

### 3.3. `TestProcessor` (`src/main/java/com/example/TestProcessor.java`)

*   **Role**: Implements the core business logic for handling OData data requests (CRUD operations). It implements `EntityCollectionProcessor` and `EntityProcessor`.
*   **Functionality**:
    *   **`readEntityCollection`**: Retrieves a collection of `Product` entities from the database. Supports basic `$filter` (on `Price`) and `$orderby` (on `Price desc`) query options.
    *   **`readEntity`**: Retrieves a single `Product` entity based on its key (ID).
    *   **`createEntity`**: Inserts a new `Product` entity into the database. Parses JSON request body for entity properties.
    *   **`updateEntity`**: Updates an existing `Product` entity. Supports partial updates by merging incoming JSON data with existing data.
    *   **`deleteEntity`**: Deletes a `Product` entity based on its key.
    *   Interacts with the database via `DatabaseHelper`.
    *   Uses Olingo serializers to format responses.

### 3.4. `DatabaseHelper` (`src/main/java/com/example/DatabaseHelper.java`)

*   **Role**: Manages database connections and schema initialization.
*   **Functionality**:
    *   Provides a static method `getConnection()` to establish a connection to the H2 database (`jdbc:h2:file:./testdb`).
    *   Includes a `createTable()` method to create the `PRODUCTS` table and pre-populate it with sample data if it doesn't exist. This method is primarily used for testing setup.

### 3.5. `ODataQueryBuilder` (`src/main/java/com/example/ODataQueryBuilder.java`)

*   **Role**: A utility class for programmatically constructing OData query strings.
*   **Functionality**:
    *   Provides a fluent API to add OData system query options (`$filter`, `$orderby`, `$select`, `$top`, `$skip`).
    *   Handles URL encoding of query parameters.
    *   Builds the complete OData URL.
*   **Usage**: Likely used for internal testing or client-side query generation, not for processing incoming requests.

## 4. Data Flow (Read Entity Collection Example)

1.  A client sends an HTTP GET request to `/odata/Products` (e.g., `GET /odata/Products?$filter=Price gt 1000`).
2.  The `web.xml` configuration routes the request to `ODataServlet`.
3.  `ODataServlet` passes the request to the Olingo `ODataHttpHandler`.
4.  The `ODataHttpHandler` parses the URI and identifies that an `EntityCollection` is requested for the `Products` entity set.
5.  The handler invokes the `readEntityCollection` method of the registered `TestProcessor`.
6.  `TestProcessor` extracts query parameters (e.g., `$filter`) from the `UriInfo` object.
7.  `TestProcessor` calls `DatabaseHelper.getConnection()` to get a database connection.
8.  `TestProcessor` constructs an SQL query (e.g., `SELECT * FROM PRODUCTS WHERE Price > 1000`) and executes it.
9.  The `ResultSet` is processed, and `Product` entities are created as Olingo `Entity` objects.
10. `TestProcessor` uses an Olingo `ODataSerializer` to serialize the `EntityCollection` into the requested format (e.g., JSON).
11. The serialized content is set as the response body, and the appropriate HTTP status code (200 OK) and Content-Type header are set.
12. The response is sent back to the client.

## 5. Build and Deployment

*   The project uses Gradle.
*   `build.gradle` defines dependencies, including Olingo libraries, H2 database, and testing frameworks.
*   The `war` plugin is used to build a Web Archive (`.war`) file, which can be deployed to any Java Servlet Container (e.g., Tomcat, Jetty).
*   Java 21 is required for compilation and execution.

## 6. Potential Improvements / Future Work

*   Replace `System.out.println` with proper logging using SLF4J/Logback.
*   Implement more comprehensive OData query options (e.g., `$top`, `$skip`, `$count`, `$expand`, more complex `$filter` expressions).
*   Add robust error handling and custom error messages.
*   Implement authentication and authorization.
*   Consider using a connection pool for database connections.
*   Improve SQL injection prevention if dynamic SQL is used beyond prepared statements.
*   Externalize database configuration.
*   Add more comprehensive unit and integration tests.
