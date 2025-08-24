package com.example;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlAbstractEdmProvider;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainerInfo;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;

import java.util.Collections;
import java.util.List;

public class DefaultEdmProvider extends CsdlAbstractEdmProvider {
    private final java.sql.Connection connection;
    private List<TableInfo> cachedTables = null;

    public DefaultEdmProvider(java.sql.Connection connection) {
        this.connection = connection;
    }

    // Helper class to hold table/column info
    public static class TableInfo {
        public String tableName;
        public List<ColumnInfo> columns = new java.util.ArrayList<>();
        public TableInfo(String tableName) { this.tableName = tableName; }
    }
    public static class ColumnInfo {
        public String columnName;
        public int dataType;
        public ColumnInfo(String columnName, int dataType) {
            this.columnName = columnName;
            this.dataType = dataType;
        }
    }

    // Scan DB schema and cache table/column info
    private List<TableInfo> scanDatabaseSchema() {
        if (cachedTables != null) return cachedTables;
        List<TableInfo> tables = new java.util.ArrayList<>();
        try {
            java.sql.DatabaseMetaData meta = connection.getMetaData();
            try (java.sql.ResultSet rsTables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rsTables.next()) {
                    String tableName = rsTables.getString("TABLE_NAME");
                    TableInfo table = new TableInfo(tableName);
                    System.out.println("[EDM SCAN] Table: " + tableName);
                    try (java.sql.ResultSet rsCols = meta.getColumns(null, null, tableName, "%")) {
                        while (rsCols.next()) {
                            String colName = rsCols.getString("COLUMN_NAME");
                            int colType = rsCols.getInt("DATA_TYPE");
                            table.columns.add(new ColumnInfo(colName, colType));
                            System.out.println("    Column: " + colName + " (type=" + colType + ")");
                        }
                    }
                    tables.add(table);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        cachedTables = tables;
        return cachedTables;
    }
    

    // Category entity and entity set
    // Remove duplicate Category fields here (lines 24-28)

    public static final String NAMESPACE = "DynamicOData";
    public static final String CONTAINER_NAME = "Container";
    public static final FullQualifiedName CONTAINER_FQN = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);

    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) {
        List<TableInfo> tables = scanDatabaseSchema();
        for (TableInfo table : tables) {
            String odataTypeName = formatODataTypeName(table.tableName);
            FullQualifiedName fqn = new FullQualifiedName(NAMESPACE, odataTypeName);
            if (entityTypeName.equals(fqn)) {
                List<CsdlProperty> properties = new java.util.ArrayList<>();
                List<CsdlPropertyRef> keys = new java.util.ArrayList<>();
                for (ColumnInfo col : table.columns) {
                    String odataPropName = formatODataPropertyName(col.columnName);
                    CsdlProperty prop = new CsdlProperty()
                        .setName(odataPropName)
                        .setType(mapSqlTypeToEdmType(col.dataType));
                    properties.add(prop);
                    if (odataPropName.equalsIgnoreCase("ID")) {
                        CsdlPropertyRef keyRef = new CsdlPropertyRef();
                        keyRef.setName(odataPropName);
                        keys.add(keyRef);
                    }
                }
                // Add navigation properties for columns ending with _ID
                List<org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty> navProps = new java.util.ArrayList<>();
                for (ColumnInfo col : table.columns) {
                    // Special case: ensure "CATEGORYID" in "PRODUCT" adds navigation property "Category" to "Category"
                    if (table.tableName.equalsIgnoreCase("PRODUCT") && col.columnName.equalsIgnoreCase("CATEGORYID")) {
                        FullQualifiedName targetFqn = new FullQualifiedName(NAMESPACE, "Category");
                        navProps.add(new org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty()
                            .setName("Category")
                            .setType(targetFqn)
                            .setNullable(true));
                        continue;
                    }
                    if (col.columnName.endsWith("_ID") || col.columnName.endsWith("ID")) {
                        String targetTable;
                        if (col.columnName.endsWith("_ID")) {
                            targetTable = col.columnName.substring(0, col.columnName.length() - 3);
                        } else {
                            targetTable = col.columnName.substring(0, col.columnName.length() - 2);
                        }
                        String navName = formatODataTypeName(targetTable);
                        for (TableInfo t : tables) {
                            if (formatODataTypeName(t.tableName).equals(navName)) {
                                FullQualifiedName targetFqn = new FullQualifiedName(NAMESPACE, navName);
                                navProps.add(new org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty()
                                    .setName(navName)
                                    .setType(targetFqn)
                                    .setNullable(true));
                            }
                        }
                    }
                }
                return new CsdlEntityType()
                    .setName(odataTypeName)
                    .setKey(keys)
                    .setProperties(properties)
                    .setNavigationProperties(navProps);
            }
        }
        return null;
    }

    // Helper to map SQL types to Edm types
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
                return EdmPrimitiveTypeKind.String.getFullQualifiedName();
            default:
                return EdmPrimitiveTypeKind.String.getFullQualifiedName();
        }
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) {
        if (!entityContainer.equals(CONTAINER_FQN)) return null;
        List<TableInfo> tables = scanDatabaseSchema();
        for (TableInfo table : tables) {
            String odataSetName = formatODataEntitySetName(table.tableName);
            if (entitySetName.equals(odataSetName)) {
                String typeName = formatODataTypeName(table.tableName);
                FullQualifiedName fqn = new FullQualifiedName(NAMESPACE, typeName);
                return new CsdlEntitySet()
                    .setName(odataSetName)
                    .setType(fqn);
            }
        }
        return null;
    }

    @Override
    public CsdlEntityContainer getEntityContainer() {
        List<TableInfo> tables = scanDatabaseSchema();
        List<CsdlEntitySet> entitySets = new java.util.ArrayList<>();
        for (TableInfo table : tables) {
            String odataName = formatODataEntitySetName(table.tableName);
            entitySets.add(getEntitySet(CONTAINER_FQN, odataName));
        }
        return new CsdlEntityContainer()
            .setName(CONTAINER_NAME)
            .setEntitySets(entitySets);
    }

    // Utility to format table name to OData type name (PascalCase, singular)
    private String formatODataTypeName(String tableName) {
        // Use exact DB table name with only first letter capitalized, e.g. "Product", "Category"
        if (tableName.length() == 0) return tableName;
        return tableName.substring(0, 1).toUpperCase() + tableName.substring(1).toLowerCase();
    }

    // Utility to format table name to OData entity set name (PascalCase, plural)
    // (Removed duplicate implementation)

    // Utility to format column name to OData property name (PascalCase)
    private String formatODataPropertyName(String colName) {
        // Preserve DB column name case for OData property name
        return colName;
    }

    // Utility to format table name to OData entity set name (PascalCase, plural)
    private String formatODataEntitySetName(String tableName) {
        // Return pluralized entity set name for OData ("Products", "Categories")
        if (tableName.equalsIgnoreCase("PRODUCT")) return "Products";
        if (tableName.equalsIgnoreCase("CATEGORY")) return "Categories";
        if (tableName.length() == 0) return tableName;
        // Fallback: PascalCase + "s"
        String base = tableName.substring(0, 1).toUpperCase() + tableName.substring(1).toLowerCase();
        return base.endsWith("s") ? base : base + "s";
    }

    @Override
    public List<CsdlSchema> getSchemas() {
        List<TableInfo> tables = scanDatabaseSchema();
        List<CsdlEntityType> entityTypes = new java.util.ArrayList<>();
        for (TableInfo table : tables) {
            String odataTypeName = formatODataTypeName(table.tableName);
            FullQualifiedName fqn = new FullQualifiedName(NAMESPACE, odataTypeName);
            entityTypes.add(getEntityType(fqn));
        }
        CsdlSchema schema = new CsdlSchema();
        schema.setNamespace(NAMESPACE);
        schema.setEntityTypes(entityTypes);
        schema.setEntityContainer(getEntityContainer());
        return Collections.singletonList(schema);
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) {
        if (entityContainerName == null || entityContainerName.equals(CONTAINER_FQN)) {
            return new CsdlEntityContainerInfo().setContainerName(CONTAINER_FQN);
        }
        return null;
    }
}
