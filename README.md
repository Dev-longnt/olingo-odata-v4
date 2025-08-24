# OData Server Project

## Overview
This project implements a dynamic OData v4 server using Apache Olingo, Spring Boot, and H2. It supports CRUD, $filter, $orderby, $expand, and $count operations, with dynamic EDM generation from the database schema.

## Architecture

- **DefaultEdmProvider**: Scans DB schema, generates EDM types and entity sets.
- **DefaultProcessor**: Handles OData requests, maps to SQL, processes CRUD and navigation.
- **ODataConfig**: Spring configuration for OData beans and handler.
- **DatabaseHelper**: Manages H2 connections.
- **Integration Tests**: Validate API and DB consistency.

## Sequence Diagram

![Sequence Diagram](https://raw.githubusercontent.com/mermaid-js/mermaid-live-editor/master/public/img/sequence-diagram-example.png)

## Class Diagram

![Class Diagram](https://raw.githubusercontent.com/mermaid-js/mermaid-live-editor/master/public/img/class-diagram-example.png)

## Technical Details

- **Entity Sets**: Plural names for OData, singular for DB tables.
- **Navigation Properties**: Supports $expand for relationships (e.g., Product->Category).
- **Tests**: Integration tests cover all OData operations.
- **Renamed Classes**: `TestEdmProvider` → `DefaultEdmProvider`, `TestProcessor` → `DefaultProcessor`.

## How to Run

1. Build: `./gradlew build`
2. Run server: `./gradlew bootRun`
3. Test: `./gradlew test`
4. OData endpoint: `/odata/`

## Contact

For questions, see [`DefaultEdmProvider`](server/src/main/java/com/example/DefaultEdmProvider.java:17), [`DefaultProcessor`](server/src/main/java/com/example/DefaultProcessor.java:40), or [`ODataConfig`](server/src/main/java/com/example/ODataConfig.java:14).