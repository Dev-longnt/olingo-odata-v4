package com.example;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultProcessor implements EntityCollectionProcessor, EntityProcessor {
  @Autowired private DataSource dataSource;

  private static final Logger logger = LoggerFactory.getLogger(DefaultProcessor.class);

  private OData odata;
  private ServiceMetadata serviceMetadata;

  @Override
  public void init(OData odata, ServiceMetadata serviceMetadata) {
    this.odata = odata;
    this.serviceMetadata = serviceMetadata;
  }

  @Override
  public void readEntityCollection(
      ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
      throws SerializerException {
    List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
    UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.getFirst();
    EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

    try {
      EntityCollection entitySet = getData(edmEntitySet, null, uriInfo);

      ODataSerializer serializer = odata.createSerializer(responseFormat);

      EdmEntityType edmEntityType = edmEntitySet.getEntityType();
      ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();

      final String id = request.getRawBaseUri() + "/" + edmEntitySet.getName();
      EntityCollectionSerializerOptions opts =
          EntityCollectionSerializerOptions.with()
              .id(id)
              .contextURL(contextUrl)
              .count(uriInfo.getCountOption())
              .build();
      SerializerResult serializerResult =
          serializer.entityCollection(serviceMetadata, edmEntityType, entitySet, opts);

      response.setContent(serializerResult.getContent());
      response.setStatusCode(HttpStatusCode.OK.getStatusCode());
      response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    } catch (ODataApplicationException ex) {
      response.setStatusCode(ex.getStatusCode());
      response.setContent(
          new java.io.ByteArrayInputStream(
              ("{\"error\":{\"code\":null,\"message\":\"" + ex.getMessage() + "\"}}")
                  .getBytes(StandardCharsets.UTF_8)));
      response.setHeader(HttpHeader.CONTENT_TYPE, "application/json");
    }
  }

  @Override
  public void readEntity(
      ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
      throws ODataApplicationException, SerializerException {
    List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
    UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.getFirst();
    EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

    List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
    EntityCollection entityCollection = getData(edmEntitySet, keyPredicates, uriInfo);
    logger.debug("entityCollection size={}", entityCollection.getEntities().size());
    if (entityCollection.getEntities().isEmpty()) {
      logger.debug("No entity found for key {}", keyPredicates);
      response.setStatusCode(HttpStatusCode.NOT_FOUND.getStatusCode());
      response.setContent(null);
      return;
    }
    Entity entity = entityCollection.getEntities().getFirst();
    logger.debug("Entity found: {}", entity);
    for (Property prop : entity.getProperties()) {
      logger.debug("Property {} = {}", prop.getName(), prop.getValue());
    }

    ODataSerializer serializer = odata.createSerializer(responseFormat);
    EdmEntityType edmEntityType = edmEntitySet.getEntityType();
    ContextURL contextUrl =
        ContextURL.with()
            .entitySet(edmEntitySet)
            .navOrPropertyPath(entity.getId().toString())
            .build();

    EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();
    SerializerResult serializerResult =
        serializer.entity(serviceMetadata, edmEntityType, entity, options);
    java.io.InputStream contentStream = serializerResult.getContent();
    String contentStr;
    java.io.InputStream responseStream;
    try {
      contentStr = new String(contentStream.readAllBytes(), StandardCharsets.UTF_8);
      logger.debug("Serialized OData response: {}", contentStr);
      // Re-create InputStream for response
      responseStream = new java.io.ByteArrayInputStream(contentStr.getBytes(StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      logger.error("IOException reading OData response: {}", e.getMessage(), e);
      responseStream = serializerResult.getContent(); // fallback
    }

    response.setContent(responseStream);
    response.setStatusCode(HttpStatusCode.OK.getStatusCode());
    response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
  }

  private EntityCollection getData(
      EdmEntitySet edmEntitySet,
      List<UriParameter> keyParams,
      org.apache.olingo.server.api.uri.UriInfo uriInfo)
      throws org.apache.olingo.server.api.ODataApplicationException {
    EntityCollection entityCollection = new EntityCollection();
    logger.debug(
        "getData called: edmEntitySet={}, keyParams={}, uriInfo={}",
        (edmEntitySet != null ? edmEntitySet.getName() : "null"),
        (keyParams != null ? keyParams.toString() : "null"),
        (uriInfo != null ? uriInfo.toString() : "null"));

    EdmEntityType edmEntityType = edmEntitySet.getEntityType();
    String entitySetName = edmEntitySet.getName();
    String tableName = getTableNameFromEntitySetName(entitySetName);

    try (Connection conn = dataSource.getConnection()) {
      StringBuilder selectColumns = new StringBuilder();
      StringBuilder joinClause = new StringBuilder();
      String mainTableAlias = "T"; // Alias for the main table
      boolean hasExpand = false;

      for (String propertyName : edmEntityType.getPropertyNames()) {
        org.apache.olingo.commons.api.edm.EdmProperty edmProperty =
            edmEntityType.getStructuralProperty(propertyName);
        if (edmProperty != null) {
          if (!selectColumns.isEmpty()) {
            selectColumns.append(", ");
          }
          selectColumns.append(mainTableAlias).append(".").append(edmProperty.getName());
        }
      }

      if (uriInfo.getExpandOption() != null && uriInfo.getExpandOption().getText() != null) {
        String expandText = uriInfo.getExpandOption().getText();
        logger.debug("Expand option text: {}", expandText);

        org.apache.olingo.commons.api.edm.EdmNavigationProperty navProp =
            edmEntityType.getNavigationProperty(expandText);
        if (navProp != null) {
          hasExpand = true;
          EdmBindingTarget target = edmEntitySet.getRelatedBindingTarget(expandText);
          if (target instanceof EdmEntitySet) {
            EdmEntitySet targetEntitySet = (EdmEntitySet) target;
            String targetTableName = getTableNameFromEntitySetName(targetEntitySet.getName());
            String targetTableAlias = "J";

            org.apache.olingo.commons.api.edm.EdmEntityType targetEntityType = targetEntitySet.getEntityType();
            for (String propertyName : targetEntityType.getPropertyNames()) {
              org.apache.olingo.commons.api.edm.EdmProperty targetEdmProperty =
                  targetEntityType.getStructuralProperty(propertyName);
              if (targetEdmProperty != null) {
                selectColumns.append(", ");
                selectColumns
                    .append(targetTableAlias)
                    .append(".")
                    .append(targetEdmProperty.getName())
                    .append(" AS \"")
                    .append(navProp.getName())
                    .append("_")
                    .append(targetEdmProperty.getName())
                    .append("\"");
              }
            }

            String foreignKeyColumn = null;
            for (String propName : edmEntityType.getPropertyNames()) {
              if (propName.equalsIgnoreCase(navProp.getName() + "Id")
                  || propName.equalsIgnoreCase(navProp.getName() + "ID")) {
                foreignKeyColumn = propName;
                break;
              }
            }
            if (foreignKeyColumn == null) {
              foreignKeyColumn = navProp.getName() + "ID"; // fallback
            }
            joinClause
                .append(" LEFT JOIN ")
                .append(targetTableName)
                .append(" ")
                .append(targetTableAlias)
                .append(" ON ")
                .append(mainTableAlias)
                .append(".")
                .append(foreignKeyColumn)
                .append(" = ")
                .append(targetTableAlias)
                .append(".ID");
          }
        }
      }

      StringBuilder sql = new StringBuilder();
      sql.append("SELECT ")
          .append(selectColumns)
          .append(" FROM ")
          .append(tableName)
          .append(" ")
          .append(mainTableAlias);
      sql.append(joinClause);
      boolean hasWhere = false;
      String idCol = mainTableAlias + ".ID";

      if (keyParams != null && !keyParams.isEmpty()) {
        sql.append(" WHERE ").append(idCol).append(" = ?");
        hasWhere = true;
      }

      List<Object> filterParams = new ArrayList<>();
      if (uriInfo.getFilterOption() != null) {
        String filterExpression = uriInfo.getFilterOption().getText();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)\\s+(gt|lt|eq)\\s+([\\w.'0-9]+)").matcher(filterExpression);
        if (m.matches()) {
          String property = m.group(1);
          String operator = m.group(2);
          String value = m.group(3);

          if (edmEntityType.getProperty(property) != null) {
            String sqlOperator;
            switch (operator) {
              case "gt":
                sqlOperator = ">";
                break;
              case "lt":
                sqlOperator = "<";
                break;
              case "eq":
                sqlOperator = "=";
                break;
              default:
                throw new ODataApplicationException(
                    "Unsupported operator: " + operator, HttpStatusCode.BAD_REQUEST.getStatusCode(), null);
            }

            sql.append(hasWhere ? " AND " : " WHERE ");
            sql.append(mainTableAlias).append(".").append(property).append(" ").append(sqlOperator).append(" ?");
            hasWhere = true;

            try {
              filterParams.add(Double.parseDouble(value));
            } catch (NumberFormatException e) {
              filterParams.add(value.replace("'", ""));
            }
          } else {
            logger.warn("Filter property '{}' not found in entity type '{}'", property, edmEntityType.getName());
          }
        } else {
          logger.warn("Unsupported filter expression: {}", filterExpression);
        }
      }

      if (uriInfo.getOrderByOption() != null) {
        String orderByExpression = uriInfo.getOrderByOption().getText();
        String[] parts = orderByExpression.split("\\s+");
        if (parts.length > 0) {
          String property = parts[0];
          if (edmEntityType.getProperty(property) != null) {
            String direction = parts.length > 1 ? parts[1].toUpperCase() : "ASC";
            if (direction.equals("ASC") || direction.equals("DESC")) {
              sql.append(" ORDER BY ").append(mainTableAlias).append(".").append(property).append(" ").append(direction);
            }
          } else {
            logger.warn("OrderBy property '{}' not found in entity type '{}'", property, edmEntityType.getName());
          }
        }
      }

      try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
        int paramCount = 0;
        if (keyParams != null && !keyParams.isEmpty()) {
          paramCount++;
          stmt.setObject(paramCount, Integer.parseInt(keyParams.getFirst().getText()));
        }
        for (Object param : filterParams) {
          paramCount++;
          stmt.setObject(paramCount, param);
        }

        ResultSet rs = stmt.executeQuery();
        logger.debug("getData: Final SQL: {}", sql);
        logger.debug(
            "getData: Executing SQL: {}, param={}",
            sql,
            (keyParams != null && !keyParams.isEmpty() ? keyParams.getFirst().getText() : "none"));

        boolean foundAny = false;
        while (rs.next()) {
          Entity currentEntity = new Entity();
          for (String propertyName : edmEntityType.getPropertyNames()) {
            org.apache.olingo.commons.api.edm.EdmProperty edmProperty =
                edmEntityType.getStructuralProperty(propertyName);
            if (edmProperty != null) {
              Object value = getResultSetValue(rs, propertyName, edmProperty.getType());
              if (value != null) {
                currentEntity.addProperty(new Property(null, propertyName, ValueType.PRIMITIVE, value));
              }
            }
          }

          try {
            List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs =
                edmEntityType.getKeyPropertyRefs();
            if (!keyRefs.isEmpty()) {
              String keyName = keyRefs.getFirst().getName();
              Object keyValue = rs.getObject(keyName);
              currentEntity.setId(java.net.URI.create(entitySetName + "(" + keyValue + ")"));
            } else {
              logger.warn("No key property found for entity set {}", entitySetName);
            }
          } catch (SQLException e) {
            logger.warn("Could not set entity ID: {}", e.getMessage());
          }

          if (hasExpand) {
            String expandText = uriInfo.getExpandOption().getText();
            org.apache.olingo.commons.api.edm.EdmNavigationProperty navProp =
                edmEntityType.getNavigationProperty(expandText);
            if (navProp != null) {
              EdmBindingTarget target = edmEntitySet.getRelatedBindingTarget(expandText);
              if (target instanceof EdmEntitySet) {
                EdmEntitySet targetEntitySet = (EdmEntitySet) target;
                org.apache.olingo.commons.api.edm.EdmEntityType targetEntityType = targetEntitySet.getEntityType();
                Entity expandedEntity = new Entity();
                boolean expandedEntityHasData = false;
                for (String propertyName : targetEntityType.getPropertyNames()) {
                  org.apache.olingo.commons.api.edm.EdmProperty targetEdmProperty =
                      targetEntityType.getStructuralProperty(propertyName);
                  if (targetEdmProperty != null) {
                    String aliasedColumnName = navProp.getName() + "_" + propertyName;
                    Object value = getResultSetValue(rs, aliasedColumnName, targetEdmProperty.getType());
                    if (value != null) {
                      expandedEntity.addProperty(
                          new Property(null, propertyName, ValueType.PRIMITIVE, value));
                      expandedEntityHasData = true;
                    }
                  }
                }
                if (expandedEntityHasData) {
                  currentEntity.addProperty(
                      new Property(null, navProp.getName(), ValueType.ENTITY, expandedEntity));
                }
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
    if (uriInfo.getCountOption() != null && uriInfo.getCountOption().getValue()) {
      entityCollection.setCount(entityCollection.getEntities().size());
    }
    return entityCollection;
  }

  private String getTableNameFromEntitySetName(String entitySetName) {
    String lowerCaseName = entitySetName.toLowerCase();
    String singular;
    if (lowerCaseName.endsWith("ies")) {
        singular = lowerCaseName.substring(0, lowerCaseName.length() - 3) + "y";
    } else if (lowerCaseName.endsWith("es")) {
        singular = lowerCaseName.substring(0, lowerCaseName.length() - 2);
    } else if (lowerCaseName.endsWith("s")) {
        singular = lowerCaseName.substring(0, lowerCaseName.length() - 1);
    } else {
        singular = lowerCaseName;
    }
    return singular.toUpperCase();
  }

  private Object getResultSetValue(
      ResultSet rs, String columnName, org.apache.olingo.commons.api.edm.EdmType edmType)
      throws SQLException {
    if (edmType.getKind() == EdmTypeKind.PRIMITIVE) {
      String typeName = edmType.getName();
      try {
        if (rs.getObject(columnName) == null) {
          return null;
        }
      } catch (SQLException e) {
        logger.trace("Column '{}' not found in ResultSet.", columnName);
        return null;
      }
      switch (typeName) {
        case "Int32":
          return rs.getInt(columnName);
        case "String":
          return rs.getString(columnName);
        case "Double":
          return rs.getDouble(columnName);
        case "Boolean":
          return rs.getBoolean(columnName);
        case "Date":
          return rs.getDate(columnName);
        case "DateTimeOffset":
          return rs.getTimestamp(columnName);
        default:
          return rs.getObject(columnName);
      }
    }
    return null;
  }

  @Override
  public void createEntity(
      ODataRequest request,
      ODataResponse response,
      UriInfo uriInfo,
      ContentType requestFormat,
      ContentType responseFormat)
      throws ODataApplicationException, SerializerException {
    EdmEntitySet edmEntitySet = ((UriResourceEntitySet) uriInfo.getUriResourceParts().getFirst()).getEntitySet();
    EdmEntityType edmEntityType = edmEntitySet.getEntityType();
    String tableName = getTableNameFromEntitySetName(edmEntitySet.getName());

    try (Connection conn = dataSource.getConnection()) {
      java.io.InputStream bodyStream = request.getBody();
      String jsonString = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
      JSONObject json = new JSONObject(jsonString);
      logger.debug("createEntity raw JSON: {}", jsonString);

      StringBuilder columns = new StringBuilder();
      StringBuilder placeholders = new StringBuilder();
      List<Object> values = new ArrayList<>();

      for (String propName : edmEntityType.getPropertyNames()) {
        if (edmEntityType.getNavigationProperty(propName) == null) { // Ignore navigation properties
          if (!columns.isEmpty()) {
            columns.append(", ");
            placeholders.append(", ");
          }
          columns.append(propName);
          placeholders.append("?");
          String jsonKey = findJsonKey(json, propName);
          if (json.has(jsonKey)) {
            values.add(json.get(jsonKey));
          } else {
            values.add(null);
          }
        }
      }

      String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        for (int i = 0; i < values.size(); i++) {
          stmt.setObject(i + 1, values.get(i));
        }
        stmt.executeUpdate();
      }

      Entity entity = new Entity();
      List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs = edmEntityType.getKeyPropertyRefs();
      if (!keyRefs.isEmpty()) {
        String keyName = keyRefs.getFirst().getName();
        String jsonKey = findJsonKey(json, keyName);
        if (json.has(jsonKey)) {
          Object newEntityId = json.get(jsonKey);
          entity.setId(java.net.URI.create(edmEntitySet.getName() + "(" + newEntityId + ")"));
        }
      }

      for (String propName : edmEntityType.getPropertyNames()) {
        if (edmEntityType.getNavigationProperty(propName) == null) {
          String jsonKey = findJsonKey(json, propName);
          if (json.has(jsonKey)) {
            entity.addProperty(new Property(null, propName, ValueType.PRIMITIVE, json.get(jsonKey)));
          }
        }
      }

      ODataSerializer serializer = odata.createSerializer(responseFormat);
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

  private String findJsonKey(JSONObject json, String propName) {
    for (String key : json.keySet()) {
      if (key.equalsIgnoreCase(propName)) {
        return key;
      }
    }
    return propName; // fallback
  }

  @Override
  public void updateEntity(
      ODataRequest request,
      ODataResponse response,
      UriInfo uriInfo,
      ContentType requestFormat,
      ContentType responseFormat)
      throws ODataApplicationException, SerializerException {
    List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
    UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.getFirst();
    EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

    List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
    EdmEntityType edmEntityType = edmEntitySet.getEntityType();
    String tableName = getTableNameFromEntitySetName(edmEntitySet.getName());
    List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs =
        edmEntityType.getKeyPropertyRefs();

    try (Connection conn = dataSource.getConnection()) {
      java.io.InputStream bodyStream = request.getBody();
      String jsonString = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
      JSONObject json = new JSONObject(jsonString);
      logger.debug("updateEntity raw JSON: {}", jsonString);

      JSONObject normalizedJson = new JSONObject();
      for (String propName : edmEntityType.getPropertyNames()) {
        String jsonKey = findJsonKey(json, propName);
        if (json.has(jsonKey)) {
          normalizedJson.put(propName, json.get(jsonKey));
        }
      }

      StringBuilder updateSql = new StringBuilder("UPDATE " + tableName + " SET ");
      List<Object> values = new ArrayList<>();
      int propCount = 0;
      for (String propName : edmEntityType.getPropertyNames()) {
        if (normalizedJson.has(propName)) {
          if (propCount++ > 0) {
            updateSql.append(", ");
          }
          updateSql.append(propName).append(" = ?");
          values.add(normalizedJson.get(propName));
        }
      }

      String keyCol = keyRefs.getFirst().getName();
      updateSql.append(" WHERE ").append(keyCol).append(" = ?");
      Object keyValue = keyPredicates.getFirst().getText();
      values.add(keyValue);

      try (PreparedStatement stmt = conn.prepareStatement(updateSql.toString())) {
        for (int i = 0; i < values.size(); i++) {
          stmt.setObject(i + 1, values.get(i));
        }
        stmt.executeUpdate();
      }

      response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    } catch (Exception e) {
      logger.error("Error updating entity: {}", e.getMessage(), e);
      throw new ODataApplicationException(
          "Error updating entity: " + e.getMessage(), 500, null);
    }
  }

  @Override
  public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo)
      throws ODataApplicationException {
    List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
    UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.getFirst();
    EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
    EdmEntityType edmEntityType = edmEntitySet.getEntityType();
    String tableName = getTableNameFromEntitySetName(edmEntitySet.getName());

    List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
    List<org.apache.olingo.commons.api.edm.EdmKeyPropertyRef> keyRefs = edmEntityType.getKeyPropertyRefs();

    if (keyRefs.size() != 1) {
      throw new ODataApplicationException(
          "Unsupported composite key.", HttpStatusCode.BAD_REQUEST.getStatusCode(), null);
    }
    String keyCol = keyRefs.getFirst().getName();
    Object keyValue = keyPredicates.getFirst().getText();

    try (Connection conn = dataSource.getConnection()) {
      String sql = "DELETE FROM " + tableName + " WHERE " + keyCol + " = ?";
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, keyValue);
        stmt.executeUpdate();
      }
      response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    } catch (Exception e) {
      logger.error("Error deleting entity: {}", e.getMessage(), e);
      throw new ODataApplicationException("Error deleting entity", 500, null);
    }
  }
}