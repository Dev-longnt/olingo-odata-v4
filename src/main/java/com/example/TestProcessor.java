package com.example;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Comparator;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestProcessor implements EntityCollectionProcessor, EntityProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TestProcessor.class);

    private OData odata;
    private ServiceMetadata serviceMetadata;

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        try {
            EntityCollection entitySet = getData(edmEntitySet, null, uriInfo);

            ODataSerializer serializer = odata.createSerializer(responseFormat);

            EdmEntityType edmEntityType = edmEntitySet.getEntityType();
            ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();

            final String id = request.getRawBaseUri() + "/" + edmEntitySet.getName();
            EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with()
                    .id(id)
                    .contextURL(contextUrl)
                    .build();
            SerializerResult serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entitySet, opts);

            response.setContent(serializerResult.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (ODataApplicationException ex) {
            response.setStatusCode(ex.getStatusCode());
            response.setContent(new java.io.ByteArrayInputStream(
                ("{\"error\":{\"code\":null,\"message\":\"" + ex.getMessage() + "\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ));
            response.setHeader(HttpHeader.CONTENT_TYPE, "application/json");
        }
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        EntityCollection entityCollection = getData(edmEntitySet, keyPredicates, uriInfo);
        logger.debug("entityCollection size={}", (entityCollection != null ? entityCollection.getEntities().size() : 0));
        if (entityCollection == null || entityCollection.getEntities().isEmpty()) {
            logger.debug("No entity found for key {}", keyPredicates);
            response.setStatusCode(HttpStatusCode.NOT_FOUND.getStatusCode());
            response.setContent(null);
            return;
        }
        Entity entity = entityCollection.getEntities().get(0);
        logger.debug("Entity found: {}", entity);
        for (Property prop : entity.getProperties()) {
            logger.debug("Property {} = {}", prop.getName(), prop.getValue());
        }

        ODataSerializer serializer = odata.createSerializer(responseFormat);
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).navOrPropertyPath(entity.getId().toString()).build();

        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();
        SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, options);
        java.io.InputStream contentStream = serializerResult.getContent();
        String contentStr = null;
        java.io.InputStream responseStream = null;
        try {
            contentStr = new String(contentStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("Serialized OData response: {}", contentStr);
            // Re-create InputStream for response
            responseStream = new java.io.ByteArrayInputStream(contentStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            logger.error("IOException reading OData response: {}", e.getMessage(), e);
            responseStream = serializerResult.getContent(); // fallback
        }

        response.setContent(responseStream);
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    private EntityCollection getData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, org.apache.olingo.server.api.uri.UriInfo uriInfo) throws org.apache.olingo.server.api.ODataApplicationException {
        EntityCollection productsCollection = new EntityCollection();
        logger.debug("getData called: edmEntitySet={}, keyParams={}, uriInfo={}",
            (edmEntitySet != null ? edmEntitySet.getName() : "null"),
            (keyParams != null ? keyParams.toString() : "null"),
            (uriInfo != null ? uriInfo.toString() : "null"));
        if (TestEdmProvider.ES_PRODUCTS_NAME.equals(edmEntitySet.getName())) {
            try (Connection conn = DatabaseHelper.getConnection()) {
                StringBuilder sql = new StringBuilder("SELECT * FROM PRODUCTS");
                boolean hasWhere = false;
                // Handle key lookup
                if (keyParams != null && !keyParams.isEmpty()) {
                    sql.append(" WHERE ID = ?");
                    hasWhere = true;
                }
                // Parse OData query options from UriInfo
                String filterValue = null;
                String orderByValue = null;
                if (uriInfo.getFilterOption() != null) {
                    String rawFilterValue = uriInfo.getFilterOption().getText();
                    logger.debug("Raw filter option text: {}", rawFilterValue);
                    logger.debug("FilterOption class: {}", uriInfo.getFilterOption().getClass().getName());
                    logger.debug("FilterOption toString: {}", uriInfo.getFilterOption().toString());
                    try {
                        filterValue = java.net.URLDecoder.decode(rawFilterValue, java.nio.charset.StandardCharsets.UTF_8.toString());
                    } catch (Exception e) {
                        logger.warn("Failed to decode filterValue: {}", rawFilterValue, e);
                        filterValue = rawFilterValue;
                    }
                    logger.debug("Decoded filter option text: {}", filterValue);
                }
                if (uriInfo.getOrderByOption() != null) {
                    orderByValue = uriInfo.getOrderByOption().getText();
                    logger.debug("Order by option text: {}", orderByValue);
                }
                Double filterPrice = null;
                int paramCount = 0;

                // $filter (supports Price gt N)
                if (filterValue != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Price\\s+gt\\s+(\\d+(\\.\\d+)?)").matcher(filterValue);
                    if (m.find()) {
                        filterPrice = Double.parseDouble(m.group(1));
                        sql.append(hasWhere ? " AND " : " WHERE ");
                        sql.append("Price > ?");
                        hasWhere = true;
                    } else {
                        logger.warn("Unsupported filter expression: {}", filterValue);
                        throw new org.apache.olingo.server.api.ODataApplicationException(
                            "Unsupported filter expression",
                            org.apache.olingo.commons.api.http.HttpStatusCode.BAD_REQUEST.getStatusCode(),
                            java.util.Locale.ENGLISH
                        );
                    }
                }

                // $orderby (supports Price desc)
                if (orderByValue != null && orderByValue.equals("Price desc")) {
                    sql.append(" ORDER BY Price DESC");
                }
                
                // $select (ignored, always returns all columns)
                // $top/$skip
                // TODO: Implement $top, $skip, $count, $expand using UriInfo if needed
                // Example:
                // if (uriInfo.getTopOption() != null) { ... }
                // if (uriInfo.getSkipOption() != null) { ... }
                // if (uriInfo.getCountOption() != null) { ... }
                // For now, these features are not implemented.
                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    if (keyParams != null && !keyParams.isEmpty()) {
                        paramCount++;
                        stmt.setInt(paramCount, Integer.parseInt(keyParams.get(0).getText()));
                    }
                    if (filterPrice != null) {
                        paramCount++;
                        stmt.setDouble(paramCount, filterPrice);
                    }
                    ResultSet rs = stmt.executeQuery();
                    logger.debug("getData: Final SQL: {}", sql);
                    logger.debug("getData: Executing SQL: {}, param={}", sql, (keyParams != null && !keyParams.isEmpty() ? keyParams.get(0).getText() : "none"));
                    logger.debug("getData: FilterValue used: {}", filterValue);
                    boolean foundAny = false;
                    while (rs.next()) {
                        Entity product = new Entity()
                                .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, rs.getInt("ID")))
                                .addProperty(new Property(null, "Name", ValueType.PRIMITIVE, rs.getString("Name")))
                                .addProperty(new Property(null, "Description", ValueType.PRIMITIVE, rs.getString("Description")))
                                .addProperty(new Property(null, "Price", ValueType.PRIMITIVE, rs.getDouble("Price")));
                        // Set canonical ID URI for OData entity
                        product.setId(java.net.URI.create("Products(" + rs.getInt("ID") + ")"));
                        // TODO: Implement $expand=Category if needed
                        // if (expandCategory) {
                        //     product.addProperty(new Property(null, "Category", ValueType.PRIMITIVE, rs.getString("CategoryName")));
                        // }
                        productsCollection.getEntities().add(product);
                        foundAny = true;
                        logger.debug("getData: Found row ID={}, Name={}, Desc={}", rs.getInt("ID"), rs.getString("Name"), rs.getString("Description"));
                    }
                    if (!foundAny) {
                        logger.debug("getData: No rows found for SQL: {}", sql);
                    }
                }
                // The sorting is now handled by the database, so this is not needed.
            } catch (SQLException e) {
                logger.error("getData: SQLException: {}", e.getMessage(), e);
                throw new RuntimeException("Database error: " + e.getMessage(), e);
            }
        }
        return productsCollection;
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        if (TestEdmProvider.ES_PRODUCTS_NAME.equals(edmEntitySet.getName())) {
            try (Connection conn = DatabaseHelper.getConnection()) {
                // DatabaseHelper.createTable(); // Removed call
                java.io.InputStream bodyStream = request.getBody();
                String jsonString = new String(bodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                logger.debug("createEntity raw JSON: {}", jsonString);
                org.json.JSONObject json = new org.json.JSONObject(jsonString);
                int id = json.getInt("ID");
                String name = json.getString("Name");
                String desc = json.getString("Description");
                double price = json.has("Price") ? json.getDouble("Price") : 0.0;
                logger.debug("createEntity parsed values: id={}, name={}, desc={}, price={}", id, name, desc, price);
                String sql = "INSERT INTO PRODUCTS (ID, Name, Description, Price) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    stmt.setString(2, name);
                    stmt.setString(3, desc);
                    stmt.setDouble(4, price);
                    int rows = stmt.executeUpdate();
                    logger.debug("createEntity SQL rows affected: {}", rows);
                }
                Entity entity = new Entity()
                        .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, id))
                        .addProperty(new Property(null, "Name", ValueType.PRIMITIVE, name))
                        .addProperty(new Property(null, "Description", ValueType.PRIMITIVE, desc))
                        .addProperty(new Property(null, "Price", ValueType.PRIMITIVE, price));
                ODataSerializer serializer = odata.createSerializer(responseFormat);
                EdmEntityType edmEntityType = edmEntitySet.getEntityType();
                ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
                EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();
                SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, options);
                response.setContent(serializerResult.getContent());
                response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
            } catch (Exception e) {
                logger.error("Error creating entity: {}", e.getMessage(), e);
                throw new ODataApplicationException("Error creating entity", 500, null);
            }
        }
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        if (TestEdmProvider.ES_PRODUCTS_NAME.equals(edmEntitySet.getName())) {
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            int id = Integer.parseInt(keyPredicates.get(0).getText());
            try (Connection conn = DatabaseHelper.getConnection()) {
                // DatabaseHelper.createTable(); // Removed call
                java.io.InputStream bodyStream = request.getBody();
                String jsonString = new String(bodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonString);
                logger.debug("updateEntity raw JSON: {}", jsonString);
                // Partial update: fetch current values first
                String currentName = null, currentDesc = null;
                double currentPrice = 0.0;
                try (PreparedStatement selectStmt = conn.prepareStatement("SELECT Name, Description, Price FROM PRODUCTS WHERE ID = ?")) {
                    selectStmt.setInt(1, id);
                    ResultSet rs = selectStmt.executeQuery();
                    if (rs.next()) {
                        currentName = rs.getString("Name");
                        currentDesc = rs.getString("Description");
                        currentPrice = rs.getDouble("Price");
                    }
                }
                String name = json.has("Name") ? json.getString("Name") : currentName;
                String desc = json.has("Description") ? json.getString("Description") : currentDesc;
                double price = json.has("Price") ? json.getDouble("Price") : currentPrice;
                logger.debug("updateEntity parsed values: name={}, desc={}, price={}", name, desc, price);
                String sql = "UPDATE PRODUCTS SET Name = ?, Description = ?, Price = ? WHERE ID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, name);
                    stmt.setString(2, desc);
                    stmt.setDouble(3, price);
                    stmt.setInt(4, id);
                    int rows = stmt.executeUpdate();
                    logger.debug("updateEntity SQL rows affected: {}", rows);
                }
                // After update, check if row exists
                try (PreparedStatement checkStmt = conn.prepareStatement("SELECT * FROM PRODUCTS WHERE ID = ?")) {
                    checkStmt.setInt(1, id);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        logger.debug("updateEntity: Row exists after update for ID={}, Name={}, Desc={}", id, rs.getString("Name"), rs.getString("Description"));
                    } else {
                        logger.debug("updateEntity: Row NOT FOUND after update for ID={}", id);
                    }
                }
                response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            } catch (Exception e) {
                logger.error("Error updating entity: {}", e.getMessage(), e);
                throw new ODataApplicationException("Error updating entity: " + e.getMessage(), 500, null);
            }
        }
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        if (TestEdmProvider.ES_PRODUCTS_NAME.equals(edmEntitySet.getName())) {
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            int id = Integer.parseInt(keyPredicates.get(0).getText());
            try (Connection conn = DatabaseHelper.getConnection()) {
                // DatabaseHelper.createTable(); // Removed call
                String sql = "DELETE FROM PRODUCTS WHERE ID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }
                response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            } catch (Exception e) {
                logger.error("Error deleting entity: {}", e.getMessage(), e);
                throw new ODataApplicationException("Error deleting entity", 500, null);
            }
        }
    }
}
