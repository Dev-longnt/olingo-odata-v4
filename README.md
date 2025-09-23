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

### Local Development

1. Build: `./gradlew build`
2. Run server: `./gradlew bootRun`
3. Test: `./gradlew test`
4. OData endpoint: `/odata/`

### Docker Deployment

#### Build and Run Locally
```bash
# Build Docker image
docker build -f server/Dockerfile -t olingo-odata-v4-server .

# Run container
docker run -p 8080:8080 olingo-odata-v4-server
```

#### Using Docker Hub Image
```bash
# Pull and run from Docker Hub
docker pull <dockerhub-username>/olingo-odata-v4-server:latest
docker run -p 8080:8080 <dockerhub-username>/olingo-odata-v4-server:latest
```

#### Docker Compose (Optional)
```yaml
version: '3.8'
services:
  odata-server:
    image: <dockerhub-username>/olingo-odata-v4-server:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### CI/CD Pipeline

This project includes GitHub Actions workflows for:

- **CI**: Automated testing on every push/PR
- **Docker Release**: Automated Docker image building and publishing to Docker Hub on tags

#### Required Secrets
Configure these secrets in your GitHub repository:
- `DOCKERHUB_USERNAME`: Your Docker Hub username
- `DOCKERHUB_TOKEN`: Your Docker Hub access token

#### Deployment Process
1. Create a tag: `git tag v1.0.0`
2. Push tag: `git push origin v1.0.0`
3. GitHub Actions will automatically build and push to Docker Hub

### Health Check
- Health endpoint: `http://localhost:8080/actuator/health`
- Metrics endpoint: `http://localhost:8080/actuator/metrics`

## Contact

For questions, see [`DefaultEdmProvider`](server/src/main/java/com/example/DefaultEdmProvider.java:17), [`DefaultProcessor`](server/src/main/java/com/example/DefaultProcessor.java:40), or [`ODataConfig`](server/src/main/java/com/example/ODataConfig.java:14).