package com.example.util;

import org.dbunit.dataset.ITable;
import org.dbunit.dataset.DefaultTable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.Comparator;

public class DbUnitTestUtils {
    public static final String[] PRODUCT_COLUMNS = {"ID", "Name", "Description", "Price"};

    public static DefaultTable buildTableFromJson(JSONArray productsJson, ITable metaTable) throws Exception {
        DefaultTable table = new DefaultTable(metaTable.getTableMetaData());
        for (int i = 0; i < productsJson.length(); i++) {
            table.addRow(buildRowFromJson(productsJson.getJSONObject(i)));
        }
        return table;
    }

    public static DefaultTable buildTableFromJsonObject(JSONObject obj, ITable metaTable) throws Exception {
        DefaultTable table = new DefaultTable(metaTable.getTableMetaData());
        table.addRow(buildRowFromJson(obj));
        return table;
    }

    public static Object[] buildRowFromJson(JSONObject product) {
        Object[] rowValues = new Object[PRODUCT_COLUMNS.length];
        for (int col = 0; col < PRODUCT_COLUMNS.length; col++) {
            String colName = PRODUCT_COLUMNS[col];
            Object value = product.has(colName) ? product.get(colName) : null;
            if ("Price".equals(colName) && value != null) {
                value = Double.valueOf(value.toString());
            }
            rowValues[col] = value;
        }
        return rowValues;
    }

    public static JSONObject parseProductJson(String body) {
        if (body.trim().startsWith("{") && body.contains("\"ID\"")) {
            return new JSONObject(body);
        } else {
            JSONObject root = new JSONObject(body);
            return root.has("value") ? root.getJSONArray("value").getJSONObject(0) : root;
        }
    }

    public static DefaultTable filterDbTable(ITable dbTable, Predicate<Object[]> filter, Comparator<Object[]> order) throws Exception {
        List<Object[]> filteredRows = new ArrayList<>();
        for (int i = 0; i < dbTable.getRowCount(); i++) {
            Object[] rowValues = new Object[PRODUCT_COLUMNS.length];
            for (int col = 0; col < PRODUCT_COLUMNS.length; col++) {
                Object value = dbTable.getValue(i, PRODUCT_COLUMNS[col]);
                if ("Price".equals(PRODUCT_COLUMNS[col]) && value != null) {
                    value = Double.valueOf(value.toString());
                }
                rowValues[col] = value;
            }
            if (filter == null || filter.test(rowValues)) {
                filteredRows.add(rowValues);
            }
        }
        if (order != null) {
            filteredRows.sort(order);
        }
        DefaultTable table = new DefaultTable(dbTable.getTableMetaData());
        for (Object[] row : filteredRows) {
            table.addRow(row);
        }
        return table;
    }
}