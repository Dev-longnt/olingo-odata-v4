package com.example;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlAbstractEdmProvider;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainerInfo;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationPropertyBinding;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.commons.api.http.HttpStatusCode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultEdmProvider extends CsdlAbstractEdmProvider {

    private final DataSource dataSource;
    private final Map<String, TableInfo> cachedTables = new HashMap<>();
    private boolean schemaScanned = false;
    private CsdlEntityContainer entityContainer;

    @Value("${odata.database.schema:public}")
    private String databaseSchema;

    public static final String NAMESPACE = "OData.Demo";
    public static final String CONTAINER_NAME = "Container";
    public static final FullQualifiedName CONTAINER_FQN = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);

    public DefaultEdmProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static class TableInfo {
        String tableName;
        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();

        TableInfo(String tableName) {
            this.tableName = tableName;
        }
    }

    private static class ColumnInfo {
        String columnName;
        int dataType;

        ColumnInfo(String columnName, int dataType) {
            this.columnName = columnName;
            this.dataType = dataType;
        }
    }

    private static class ForeignKeyInfo {
        String fkColumnName;
        String pkTableName;
        String pkColumnName;
    }

    private void scanDatabaseSchema() throws SQLException {
        if (schemaScanned) {
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String[] types = { "TABLE" };
            try (ResultSet rsTables = meta.getTables(null, databaseSchema, "%", types)) {
                while (rsTables.next()) {
                    String tableName = rsTables.getString("TABLE_NAME");
                    if (tableName.equalsIgnoreCase("flyway_schema_history")) {
                        continue;
                    }
                    TableInfo table = new TableInfo(tableName);
                    cachedTables.put(tableName.toUpperCase(), table);
                }
            }

            for (TableInfo table : cachedTables.values()) {
                try (ResultSet rsCols = meta.getColumns(null, databaseSchema, table.tableName, "%")) {
                    while (rsCols.next()) {
                        table.columns.add(new ColumnInfo(rsCols.getString("COLUMN_NAME"), rsCols.getInt("DATA_TYPE")));
                    }
                }

                try (ResultSet rsPks = meta.getPrimaryKeys(null, databaseSchema, table.tableName)) {
                    while (rsPks.next()) {
                        table.primaryKeys.add(rsPks.getString("COLUMN_NAME"));
                    }
                }

                try (ResultSet rsFks = meta.getImportedKeys(null, databaseSchema, table.tableName)) {
                    while (rsFks.next()) {
                        ForeignKeyInfo fk = new ForeignKeyInfo();
                        fk.fkColumnName = rsFks.getString("FKCOLUMN_NAME");
                        fk.pkTableName = rsFks.getString("PKTABLE_NAME");
                        fk.pkColumnName = rsFks.getString("PKCOLUMN_NAME");
                        table.foreignKeys.add(fk);
                    }
                }
            }
        }
        schemaScanned = true;
    }

    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) throws ODataException {
        try {
            scanDatabaseSchema();
            TableInfo table = findTableForEntityType(entityTypeName.getName());
            if (table == null) {
                return null;
            }

            List<CsdlProperty> properties = new ArrayList<>();
            for (ColumnInfo col : table.columns) {
                properties.add(new CsdlProperty()
                        .setName(col.columnName)
                        .setType(mapSqlTypeToEdmType(col.dataType))
                        .setNullable(true));
            }

            List<CsdlPropertyRef> keys = new ArrayList<>();
            for (String pkName : table.primaryKeys) {
                keys.add(new CsdlPropertyRef().setName(pkName));
            }

            List<CsdlNavigationProperty> navProps = new ArrayList<>();
            for (ForeignKeyInfo fk : table.foreignKeys) {
                String targetTypeName = formatODataTypeName(fk.pkTableName);
                navProps.add(new CsdlNavigationProperty()
                        .setName(targetTypeName)
                        .setType(new FullQualifiedName(NAMESPACE, targetTypeName))
                        .setNullable(true));
            }

            return new CsdlEntityType()
                    .setName(entityTypeName.getName())
                    .setProperties(properties)
                    .setKey(keys)
                    .setNavigationProperties(navProps);
        } catch (SQLException e) {
            throw new ODataApplicationException("Error accessing database metadata",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e);
        }
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) throws ODataException {
        try {
            if (!entityContainer.equals(CONTAINER_FQN)) {
                return null;
            }
            scanDatabaseSchema();
            TableInfo table = findTableForEntitySet(entitySetName);
            if (table != null) {
                CsdlEntitySet csdlEntitySet = new CsdlEntitySet();
                csdlEntitySet.setName(entitySetName);
                csdlEntitySet.setType(new FullQualifiedName(NAMESPACE, formatODataTypeName(table.tableName)));

                List<CsdlNavigationPropertyBinding> navBindings = new ArrayList<>();
                for (ForeignKeyInfo fk : table.foreignKeys) {
                    String targetEntitySet = formatODataEntitySetName(fk.pkTableName);
                    navBindings.add(new CsdlNavigationPropertyBinding()
                            .setPath(formatODataTypeName(fk.pkTableName))
                            .setTarget(targetEntitySet));
                }
                csdlEntitySet.setNavigationPropertyBindings(navBindings);

                return csdlEntitySet;
            }
            return null;
        } catch (SQLException e) {
            throw new ODataApplicationException("Error accessing database metadata",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e);
        }
    }

    @Override
    public CsdlEntityContainer getEntityContainer() throws ODataException {
        if (entityContainer != null) {
            return entityContainer;
        }
        try {
            scanDatabaseSchema();
            List<CsdlEntitySet> entitySets = new ArrayList<>();
            for (TableInfo table : cachedTables.values()) {
                CsdlEntitySet entitySet = getEntitySet(CONTAINER_FQN, formatODataEntitySetName(table.tableName));
                if (entitySet != null) {
                    entitySets.add(entitySet);
                }
            }
            CsdlEntityContainer container = new CsdlEntityContainer();
            container.setName(CONTAINER_NAME);
            container.setEntitySets(entitySets);
            entityContainer = container;
            return entityContainer;
        } catch (SQLException e) {
            throw new ODataApplicationException("Error accessing database metadata",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e);
        }
    }

    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {
        try {
            scanDatabaseSchema();
            CsdlSchema schema = new CsdlSchema();
            schema.setNamespace(NAMESPACE);

            List<CsdlEntityType> entityTypes = new ArrayList<>();
            for (TableInfo table : cachedTables.values()) {
                entityTypes.add(getEntityType(new FullQualifiedName(NAMESPACE, formatODataTypeName(table.tableName))));
            }
            schema.setEntityTypes(entityTypes);
            schema.setEntityContainer(getEntityContainer());

            return Collections.singletonList(schema);
        } catch (SQLException e) {
            throw new ODataApplicationException("Error accessing database metadata",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e);
        }
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) {
        if (entityContainerName == null || entityContainerName.equals(CONTAINER_FQN)) {
            return new CsdlEntityContainerInfo().setContainerName(CONTAINER_FQN);
        }
        return null;
    }

    private TableInfo findTableForEntityType(String entityTypeName) {
        for (TableInfo table : cachedTables.values()) {
            if (formatODataTypeName(table.tableName).equalsIgnoreCase(entityTypeName)) {
                return table;
            }
        }
        return null;
    }

    private TableInfo findTableForEntitySet(String entitySetName) {
        for (TableInfo table : cachedTables.values()) {
            if (formatODataEntitySetName(table.tableName).equalsIgnoreCase(entitySetName)) {
                return table;
            }
        }
        return null;
    }

    private String formatODataTypeName(String tableName) {
        if (tableName == null || tableName.isEmpty())
            return tableName;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : tableName.toLowerCase().toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Get the actual database table name for a given OData entity set name
     * This method provides the reverse mapping from entity set name to database
     * table name
     */
    public String getActualTableNameForEntitySet(String entitySetName) {
        try {
            scanDatabaseSchema();
            for (TableInfo table : cachedTables.values()) {
                if (formatODataEntitySetName(table.tableName).equalsIgnoreCase(entitySetName)) {
                    // Return table name with schema prefix
                    return databaseSchema + "." + table.tableName;
                }
            }
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    private String formatODataEntitySetName(String tableName) {
        String singularName = formatODataTypeName(tableName);
        if (singularName.endsWith("y")) {
            return singularName.substring(0, singularName.length() - 1) + "ies";
        } else if (singularName.endsWith("s")) {
            return singularName + "es";
        } else {
            return singularName + "s";
        }
    }

    private FullQualifiedName mapSqlTypeToEdmType(int sqlType) {
        switch (sqlType) {
            case java.sql.Types.INTEGER:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.TINYINT:
                return EdmPrimitiveTypeKind.Int32.getFullQualifiedName();
            case java.sql.Types.BIGINT:
                return EdmPrimitiveTypeKind.Int64.getFullQualifiedName();
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
            case java.sql.Types.DOUBLE:
                return EdmPrimitiveTypeKind.Double.getFullQualifiedName();
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
                return EdmPrimitiveTypeKind.Decimal.getFullQualifiedName();
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT:
                return EdmPrimitiveTypeKind.Boolean.getFullQualifiedName();
            case java.sql.Types.DATE:
                return EdmPrimitiveTypeKind.Date.getFullQualifiedName();
            case java.sql.Types.TIMESTAMP:
                return EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName();
            case java.sql.Types.VARCHAR:
            case java.sql.Types.CHAR:
            case java.sql.Types.LONGVARCHAR:
            case java.sql.Types.NVARCHAR:
            case java.sql.Types.NCHAR:
            case java.sql.Types.LONGNVARCHAR:
                return EdmPrimitiveTypeKind.String.getFullQualifiedName();
            default:
                return EdmPrimitiveTypeKind.String.getFullQualifiedName();
        }
    }
}
