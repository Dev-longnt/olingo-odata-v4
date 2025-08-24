package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultProcessor implements EntityCollectionProcessor, EntityProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProcessor.class);

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
                    .count(uriInfo.getCountOption())
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
        EntityCollection entityCollection = new EntityCollection();
        logger.debug("getData called: edmEntitySet={}, keyParams={}, uriInfo={}",
            (edmEntitySet != null ? edmEntitySet.getName() : "null"),
            (keyParams != null ? keyParams.toString() : "null"),
            (uriInfo != null ? uriInfo.toString() : "null"));

        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        String entitySetName = edmEntitySet.getName();
        String tableName = getTableNameFromEntitySetName(entitySetName);

        try (Connection conn = DatabaseHelper.getConnection()) {
            StringBuilder selectColumns = new StringBuilder();
            StringBuilder joinClause = new StringBuilder();
            String mainTableAlias = "T"; // Alias for the main table
            boolean hasExpand = false;

            // Add properties of the main entity type to SELECT clause
            for (String propertyName : edmEntityType.getPropertyNames()) {
                org.apache.olingo.commons.api.edm.EdmProperty edmProperty = edmEntityType.getStructuralProperty(propertyName);
                if (selectColumns.length() > 0) {
                    selectColumns.append(", ");
                }
                selectColumns.append(mainTableAlias).append(".").append(edmProperty.getName());
            }

            // Handle $expand
            if (uriInfo.getExpandOption() != null && uriInfo.getExpandOption().getText() != null) {
                String expandText = uriInfo.getExpandOption().getText();
                logger.debug("Expand option text: {}", expandText);

                // This is a simplified handling for single-level expand.
                // A more robust solution would involve parsing the expand tree.
                // Assuming expandText is something like "Category"
                // Need to find the navigation property from edmEntityType
                org.apache.olingo.commons.api.edm.EdmNavigationProperty navProp = edmEntityType.getNavigationProperty(expandText);
                if (navProp != null) {
                    hasExpand = true;
                    String targetEntityTypeName = navProp.getType().getName();
                    String targetTableName;
                    if (entitySetName.equalsIgnoreCase("Products") && expandText.equalsIgnoreCase("Category")) {
                        targetTableName = "CATEGORY";
                    } else if (targetEntityTypeName.equalsIgnoreCase("Category")) {
                        targetTableName = "CATEGORY";
                    } else {
                        String targetEntitySetName = targetEntityTypeName + "s";
                        targetTableName = getTableNameFromEntitySetName(targetEntitySetName);
                    }
                    String targetTableAlias = "J"; // Alias for the joined table

                    // Add columns from the expanded entity to SELECT clause
                    org.apache.olingo.commons.api.edm.EdmEntityType targetEntityType = (org.apache.olingo.commons.api.edm.EdmEntityType) navProp.getType();
                    for (String propertyName : targetEntityType.getPropertyNames()) {
                        org.apache.olingo.commons.api.edm.EdmProperty targetEdmProperty = targetEntityType.getStructuralProperty(propertyName);
                        selectColumns.append(", ");
                        selectColumns.append(targetTableAlias).append(".").append(targetEdmProperty.getName())
                                     .append(" AS ").append(navProp.getName()).append(targetEdmProperty.getName()); // Alias to avoid name collision
                    }

                    // Construct JOIN clause
                    // This assumes a foreign key relationship where main table has a column like TargetEntityName_ID
                    // and target table has an ID column. This needs to be more robust.
                    // For now, let's assume the navigation property name is the foreign key column name + "ID"
                    // e.g., Category -> CategoryID
                    // Find the actual DB column name for the foreign key by scanning entity properties
                    String foreignKeyColumn = null;
                    for (String propName : edmEntityType.getPropertyNames()) {
                        if (propName.equalsIgnoreCase(navProp.getName() + "Id") || propName.equalsIgnoreCase(navProp.getName() + "ID")) {
                            foreignKeyColumn = propName;
                            break;
                        }
                    }
                    if (foreignKeyColumn == null) {
                        foreignKeyColumn = navProp.getName() + "ID"; // fallback
                    }
                    joinClause.append(" LEFT JOIN ").append(targetTableName).append(" ").append(targetTableAlias)
                              .append(" ON ").append(mainTableAlias).append(".").append(foreignKeyColumn)
                              .append(" = ").append(targetTableAlias).append(".ID");
                }
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(selectColumns).append(" FROM ").append(tableName).append(" ").append(mainTableAlias);
            sql.append(joinClause);
            boolean hasWhere = false;
            String idCol = mainTableAlias + ".ID"; // Use alias for ID column
            String priceCol = mainTableAlias + ".Price"; // Use alias for Price column

            // Handle key lookup
            if (keyParams != null && !keyParams.isEmpty()) {
                sql.append(" WHERE " + idCol + " = ?");
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
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("PRICE\\s+gt\\s+(\\d+(\\.\\d+)?)").matcher(filterValue);
                if (m.find()) {
                    filterPrice = Double.parseDouble(m.group(1));
                    sql.append(hasWhere ? " AND " : " WHERE ");
                    sql.append(priceCol + " > ?");
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
                sql.append(" ORDER BY " + priceCol + " DESC");
            }
            
            // $select (ignored, always returns all columns)
            // $top/$skip
            // TODO: Implement $top, $skip, $count using UriInfo if needed
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
                    Entity currentEntity = new Entity();
                    // Populate properties of the main entity
                    for (String propertyName : edmEntityType.getPropertyNames()) {
                        org.apache.olingo.commons.api.edm.EdmProperty edmProperty = edmEntityType.getStructuralProperty(propertyName);
                        Object value = getResultSetValue(rs, propertyName, edmProperty.getType());
                        if (value != null) {
                            currentEntity.addProperty(new Property(null, propertyName, ValueType.PRIMITIVE, value));
                        }
                    }

                    // Set canonical ID URI for OData entity
                    try {
                        List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs = edmEntityType.getKeyPropertyRefs();
                        if (!keyRefs.isEmpty()) {
                            String keyName = keyRefs.get(0).getName();
                            Object keyValue = rs.getObject(keyName);
                            currentEntity.setId(java.net.URI.create(entitySetName + "(" + keyValue + ")"));
                        } else {
                            logger.warn("No key property found for entity set {}", entitySetName);
                        }
                    } catch (SQLException e) {
                        logger.warn("Could not set entity ID: {}", e.getMessage());
                    }

                    // Handle expanded entities
                    if (hasExpand) {
                        // Assuming single-level expand for now, and expandText is the navigation property name
                        String expandText = uriInfo.getExpandOption().getText();
                        org.apache.olingo.commons.api.edm.EdmNavigationProperty navProp = edmEntityType.getNavigationProperty(expandText);
                        if (navProp != null) {
                            org.apache.olingo.commons.api.edm.EdmEntityType targetEntityType = (org.apache.olingo.commons.api.edm.EdmEntityType) navProp.getType();
                            Entity expandedEntity = new Entity();
                            boolean expandedEntityHasData = false;
                            for (String propertyName : targetEntityType.getPropertyNames()) {
                                org.apache.olingo.commons.api.edm.EdmProperty targetEdmProperty = targetEntityType.getStructuralProperty(propertyName);
                                String aliasedColumnName = navProp.getName() + propertyName; // e.g., CategoryID, CategoryName
                                Object value = getResultSetValue(rs, aliasedColumnName, targetEdmProperty.getType());
                                if (value != null) {
                                    expandedEntity.addProperty(new Property(null, propertyName, ValueType.PRIMITIVE, value));
                                    expandedEntityHasData = true;
                                }
                            }
                            if (expandedEntityHasData) {
                                currentEntity.addProperty(new Property(null, navProp.getName(), ValueType.ENTITY, expandedEntity));
                            }
                        }
                    }
                    entityCollection.getEntities().add(currentEntity);
                    foundAny = true;
                    logger.debug("getData: Found row for entity set {}", entitySetName);
                }
                if (!foundAny) {
                    logger.debug("getData: No rows found for SQL: {}", sql);
                }
            }
        } catch (SQLException e) {
            logger.error("getData: SQLException: {}", e.getMessage(), e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        // Implement $count support
        if (uriInfo.getCountOption() != null && uriInfo.getCountOption().getValue()) {
            entityCollection.setCount(entityCollection.getEntities().size());
        }
        return entityCollection;
    }

    private String getTableNameFromEntitySetName(String entitySetName) {
        // Always use singular uppercase table names
        String lowerCaseName = entitySetName.toLowerCase();
        if (lowerCaseName.endsWith("s")) {
            lowerCaseName = lowerCaseName.substring(0, lowerCaseName.length() - 1);
        }
        return lowerCaseName.toUpperCase();
    }

    private Object getResultSetValue(ResultSet rs, String columnName, org.apache.olingo.commons.api.edm.EdmType edmType) throws SQLException {
        if (edmType.getKind() == EdmTypeKind.PRIMITIVE) {
            if (edmType.getName().equals("Int32")) {
                return rs.getInt(columnName);
            } else if (edmType.getName().equals("String")) {
                return rs.getString(columnName);
            } else if (edmType.getName().equals("Double")) {
                return rs.getDouble(columnName);
            } else if (edmType.getName().equals("Boolean")) {
                return rs.getBoolean(columnName);
            } else if (edmType.getName().equals("Date")) {
                return rs.getDate(columnName);
            } else if (edmType.getName().equals("DateTimeOffset")) {
                return rs.getTimestamp(columnName);
            }
            // Add more primitive types as needed
        }
        return null;
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        if ("Products".equals(edmEntitySet.getName()) || "Product".equals(edmEntitySet.getName())) {
            try (Connection conn = DatabaseHelper.getConnection()) {
                java.io.InputStream bodyStream = request.getBody();
                String jsonString = new String(bodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                logger.debug("createEntity raw JSON: {}", jsonString);
                org.json.JSONObject json = new org.json.JSONObject(jsonString);
                int id = json.getInt("ID");
                String name = json.getString("NAME");
                String desc = json.getString("DESCRIPTION");
                double price = json.has("PRICE") ? json.getDouble("PRICE") : 0.0;
                // Insert entity
                String sql = "INSERT INTO PRODUCT (ID, Name, Description, Price) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    stmt.setString(2, name);
                    stmt.setString(3, desc);
                    stmt.setDouble(4, price);
                    int rows = stmt.executeUpdate();
                    logger.debug("createEntity SQL rows affected: {}", rows);
                }
                // Fetch inserted entity from DB to ensure all fields are populated
                Entity entity = null;
                String selectSql = "SELECT * FROM PRODUCT WHERE ID = ?";
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setInt(1, id);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            entity = new Entity();
                            entity.setId(java.net.URI.create("Product(" + id + ")"));
                            entity.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, rs.getInt("ID")));
                            entity.addProperty(new Property(null, "NAME", ValueType.PRIMITIVE, rs.getString("NAME")));
                            entity.addProperty(new Property(null, "DESCRIPTION", ValueType.PRIMITIVE, rs.getString("DESCRIPTION")));
                            entity.addProperty(new Property(null, "PRICE", ValueType.PRIMITIVE, rs.getDouble("PRICE")));
                            // Add CATEGORYID if present
                            try {
                                entity.addProperty(new Property(null, "CATEGORYID", ValueType.PRIMITIVE, rs.getObject("CATEGORYID")));
                            } catch (SQLException ignore) {}
                        }
                    }
                }
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

        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        String tableName = getTableNameFromEntitySetName(edmEntitySet.getName());
        List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs = edmEntityType.getKeyPropertyRefs();
        
        try (Connection conn = DatabaseHelper.getConnection()) {
            java.io.InputStream bodyStream = request.getBody();
            String jsonString = new String(bodyStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonString);
            logger.debug("updateEntity raw JSON: {}", jsonString);

            // Map incoming JSON keys to uppercase property names for DB columns
            JSONObject normalizedJson = new JSONObject();
            for (String propName : edmEntityType.getPropertyNames()) {
                // Accept both uppercase and camelCase from input
                Object value = json.has(propName) ? json.get(propName)
                              : (json.has(propName.toLowerCase()) ? json.get(propName.toLowerCase()) : null);
                if (value == null && propName.length() > 1) {
                    // Try camelCase (e.g., "Name" -> "NAME")
                    String camel = propName.substring(0,1).toUpperCase() + propName.substring(1).toLowerCase();
                    if (json.has(camel)) value = json.get(camel);
                }
                if (value != null) normalizedJson.put(propName, value);
            }
        
            // Build SELECT for current values
            StringBuilder selectCols = new StringBuilder();
            for (String propName : edmEntityType.getPropertyNames()) {
                if (selectCols.length() > 0) selectCols.append(", ");
                selectCols.append(propName);
            }
            String keyCol = keyRefs.get(0).getName();
            String selectSql = "SELECT " + selectCols + " FROM " + tableName + " WHERE " + keyCol + " = ?";
            Object keyValue = keyPredicates.get(0).getText();
        
            JSONObject currentValues = new JSONObject();
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setObject(1, keyValue);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    for (String propName : edmEntityType.getPropertyNames()) {
                        currentValues.put(propName, rs.getObject(propName));
                    }
                }
            }
        
            // Prepare update values
            JSONObject updateValues = new JSONObject();
            for (String propName : edmEntityType.getPropertyNames()) {
                // Always use uppercase property names for DB columns
                updateValues.put(propName, normalizedJson.has(propName) ? normalizedJson.get(propName) : currentValues.opt(propName));
            }
        
            // Build UPDATE SQL
            StringBuilder updateSql = new StringBuilder("UPDATE " + tableName + " SET ");
            int propCount = 0;
            for (String propName : edmEntityType.getPropertyNames()) {
                if (propCount++ > 0) updateSql.append(", ");
                updateSql.append(propName).append(" = ?");
            }
            updateSql.append(" WHERE ").append(keyCol).append(" = ?");
        
            try (PreparedStatement stmt = conn.prepareStatement(updateSql.toString())) {
                int idx = 1;
                for (String propName : edmEntityType.getPropertyNames()) {
                    stmt.setObject(idx++, updateValues.get(propName));
                }
                stmt.setObject(idx, keyValue);
                int rows = stmt.executeUpdate();
                logger.debug("updateEntity SQL rows affected: {}", rows);
            }
        
            // After update, check if row exists
            String checkSql = "SELECT * FROM " + tableName + " WHERE " + keyCol + " = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setObject(1, keyValue);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    logger.debug("updateEntity: Row exists after update for {}={}, values={}", keyCol, keyValue, rs);
                } else {
                    logger.debug("updateEntity: Row NOT FOUND after update for {}={}", keyCol, keyValue);
                }
            }
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        } catch (Exception e) {
            logger.error("Error updating entity: {}", e.getMessage(), e);
            throw new ODataApplicationException("Error updating entity: " + e.getMessage(), 500, null);
        }
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        if ("Products".equals(edmEntitySet.getName()) || "Product".equals(edmEntitySet.getName())) {
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            int id = Integer.parseInt(keyPredicates.get(0).getText());
            try (Connection conn = DatabaseHelper.getConnection()) {
                String sql = "DELETE FROM PRODUCT WHERE ID = ?";
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