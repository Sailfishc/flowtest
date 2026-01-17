package com.flowtest.core.snapshot;

import com.flowtest.core.fixture.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SnapshotEngine {

    private static final Logger log = LoggerFactory.getLogger(SnapshotEngine.class);

    private final JdbcTemplate jdbcTemplate;
    
    /** Default ID column name when auto-detection fails */
    private String defaultIdColumnName = "id";
    
    /** Cache for table primary key columns (detected from database metadata) */
    private final Map<String, String> tablePrimaryKeyCache = new ConcurrentHashMap<>();
    
    /** User-configured primary key columns per table (takes precedence over auto-detection) */
    private final Map<String, String> configuredPrimaryKeys = new ConcurrentHashMap<>();
    
    /** Entity metadata registry for entity class to table mapping */
    private final Map<String, EntityMetadata> entityMetadataByTable = new ConcurrentHashMap<>();

    /** Whether to capture full row data for modification detection */
    private boolean captureFullRows = true;

    /** Maximum rows to capture per table (safety limit) */
    private int maxRowsToCapture = 10000;

    public SnapshotEngine(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public SnapshotEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sets the default ID column name to use when auto-detection fails.
     * Default is "id".
     * 
     * @deprecated Use {@link #setTableIdColumn(String, String)} for per-table configuration
     *             or let the engine auto-detect the primary key.
     */
    @Deprecated
    public void setIdColumnName(String idColumnName) {
        this.defaultIdColumnName = idColumnName;
    }
    
    /**
     * Configures the primary key column for a specific table.
     * This takes precedence over auto-detection.
     * 
     * @param tableName the table name
     * @param columnName the primary key column name
     * @return this engine for fluent chaining
     */
    public SnapshotEngine setTableIdColumn(String tableName, String columnName) {
        configuredPrimaryKeys.put(tableName.toLowerCase(), columnName);
        return this;
    }
    
    /**
     * Configures primary key columns for multiple tables.
     * 
     * @param tableIdColumns map of table name to primary key column name
     * @return this engine for fluent chaining
     */
    public SnapshotEngine setTableIdColumns(Map<String, String> tableIdColumns) {
        for (Map.Entry<String, String> entry : tableIdColumns.entrySet()) {
            setTableIdColumn(entry.getKey(), entry.getValue());
        }
        return this;
    }
    
    /**
     * Registers entity metadata for a given entity class.
     * The engine will use this metadata to determine the primary key column
     * for the entity's table.
     * 
     * @param entityClass the entity class
     * @return this engine for fluent chaining
     */
    public SnapshotEngine withEntityMetadata(Class<?> entityClass) {
        EntityMetadata metadata = new EntityMetadata(entityClass);
        String tableName = metadata.getTableName().toLowerCase();
        entityMetadataByTable.put(tableName, metadata);
        // Also register the ID column from metadata
        configuredPrimaryKeys.put(tableName, metadata.getIdColumnName());
        log.debug("Registered entity metadata for {}: table={}, idColumn={}", 
            entityClass.getSimpleName(), metadata.getTableName(), metadata.getIdColumnName());
        return this;
    }
    
    /**
     * Registers entity metadata for multiple entity classes.
     * 
     * @param entityClasses the entity classes
     * @return this engine for fluent chaining
     */
    public SnapshotEngine withEntityMetadata(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            withEntityMetadata(entityClass);
        }
        return this;
    }
    
    /** Maps lowercase table names to their original case (as seen in database) */
    private final Map<String, String> originalTableNames = new ConcurrentHashMap<>();
    
    /**
     * Gets the primary key column name for a table.
     * Priority: 1) User-configured, 2) Entity metadata, 3) Auto-detected from DB, 4) Default
     * 
     * @param tableName the table name
     * @return the primary key column name
     */
    public String getIdColumnForTable(String tableName) {
        String tableKey = tableName.toLowerCase();
        
        // Remember the original table name for detection
        originalTableNames.putIfAbsent(tableKey, tableName);
        
        // 1. Check user-configured primary keys
        String configured = configuredPrimaryKeys.get(tableKey);
        if (configured != null) {
            return configured;
        }
        
        // 2. Check entity metadata (already registered in configuredPrimaryKeys via withEntityMetadata)
        // This is handled by step 1
        
        // 3. Try auto-detection from database metadata (with caching)
        String detected = tablePrimaryKeyCache.computeIfAbsent(tableKey, this::detectPrimaryKeyColumn);
        if (detected != null) {
            return detected;
        }
        
        // 4. Fallback to default
        return defaultIdColumnName;
    }
    
    /**
     * Detects the primary key column for a table using JDBC DatabaseMetaData.
     * Tries multiple case variants to handle different database case sensitivity.
     * 
     * @param tableKey the table name key (lowercase)
     * @return the primary key column name, or null if not found
     */
    private String detectPrimaryKeyColumn(String tableKey) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return null;
        }
        
        // Get the original table name if available, otherwise use the key
        String originalName = originalTableNames.getOrDefault(tableKey, tableKey);
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Try different case variants
            String[] variants = {
                originalName,                    // Original case (e.g., "T_USER_INFO")
                tableKey,                        // Lowercase (e.g., "t_user_info")
                tableKey.toUpperCase()           // Uppercase (e.g., "T_USER_INFO")
            };
            
            Set<String> tried = new HashSet<>();
            for (String variant : variants) {
                if (variant == null || !tried.add(variant)) {
                    continue; // Skip nulls and duplicates
                }
                
                String pkColumn = findPrimaryKeyFromMetadata(metaData, connection, variant);
                if (pkColumn != null) {
                    log.debug("Auto-detected primary key for table {} (tried: '{}'): {}", 
                        tableKey, variant, pkColumn);
                    return pkColumn;
                }
            }
            
            log.debug("Could not auto-detect primary key for table {}, using default: {}", 
                tableKey, defaultIdColumnName);
            return null;
        } catch (Exception e) {
            log.warn("Failed to detect primary key for table {}: {}", tableKey, e.getMessage());
            return null;
        }
    }
    
    /**
     * Finds the primary key column from database metadata.
     */
    private String findPrimaryKeyFromMetadata(DatabaseMetaData metaData, Connection connection, String tableName) 
            throws java.sql.SQLException {
        try (ResultSet rs = metaData.getPrimaryKeys(connection.getCatalog(), connection.getSchema(), tableName)) {
            if (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                // Check if there are multiple primary key columns (composite key)
                if (rs.next()) {
                    log.warn("Table {} has composite primary key, using first column: {}", tableName, columnName);
                }
                return columnName;
            }
        }
        return null;
    }

    /**
     * Sets whether to capture full row data for modification detection.
     * Default is true.
     */
    public void setCaptureFullRows(boolean captureFullRows) {
        this.captureFullRows = captureFullRows;
    }

    /**
     * Sets the maximum rows to capture per table.
     * Default is 10000.
     */
    public void setMaxRowsToCapture(int maxRowsToCapture) {
        this.maxRowsToCapture = maxRowsToCapture;
    }

    /**
     * Lists all user table names in the current database.
     */
    public Set<String> listTableNames() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return Collections.emptySet();
        }

        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = connection.getSchema();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), schema, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String tableSchema = rs.getString("TABLE_SCHEM");
                    if (isSystemSchema(tableSchema, schema)) {
                        continue;
                    }
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isEmpty()) {
                        tables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list tables: {}", e.getMessage());
        }

        return tables;
    }

    /**
     * Takes a "before" snapshot of the given tables.
     *
     * @param tables the table names to snapshot
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeBeforeSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            String idColumn = getIdColumnForTable(table);
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setMaxId(getMaxId(table, idColumn));
            snapshot.setRowCount(getRowCount(table));

            // Capture full row data if enabled
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table, idColumn);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("Before snapshot for {}: maxId={}, rowCount={}, rowDataSize={}, idColumn={}",
                table, snapshot.getMaxId(), snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size(), idColumn);
        }

        return snapshots;
    }

    /**
     * Takes an "after" snapshot of the given tables.
     *
     * @param tables the table names to snapshot
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeAfterSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            String idColumn = getIdColumnForTable(table);
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setMaxId(getMaxId(table, idColumn));
            snapshot.setRowCount(getRowCount(table));

            // Capture full row data if enabled
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table, idColumn);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("After snapshot for {}: maxId={}, rowCount={}, rowDataSize={}, idColumn={}",
                table, snapshot.getMaxId(), snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size(), idColumn);
        }

        return snapshots;
    }

    /**
     * Computes the difference between before and after snapshots.
     *
     * @param before the before snapshots
     * @param after the after snapshots
     * @return the computed diff
     */
    public SnapshotDiff computeDiff(Map<String, TableSnapshot> before, Map<String, TableSnapshot> after) {
        SnapshotDiff diff = new SnapshotDiff();

        Set<String> allTables = new LinkedHashSet<>();
        allTables.addAll(before.keySet());
        allTables.addAll(after.keySet());

        for (String table : allTables) {
            String idColumn = getIdColumnForTable(table);
            TableSnapshot beforeSnap = before.get(table);
            TableSnapshot afterSnap = after.get(table);

            long beforeMaxId = beforeSnap != null && beforeSnap.getMaxId() != null ? beforeSnap.getMaxId() : 0;
            long afterMaxId = afterSnap != null && afterSnap.getMaxId() != null ? afterSnap.getMaxId() : 0;
            long beforeCount = beforeSnap != null && beforeSnap.getRowCount() != null ? beforeSnap.getRowCount() : 0;
            long afterCount = afterSnap != null && afterSnap.getRowCount() != null ? afterSnap.getRowCount() : 0;

            // Calculate new rows based on row count difference (handles AUTO_INCREMENT gaps after rollbacks)
            // If afterCount > beforeCount, new rows were inserted
            // Also check MAX(ID) to handle mixed insert/delete scenarios
            long countBasedNewRows = Math.max(0, afterCount - beforeCount);
            long idBasedNewRows = Math.max(0, afterMaxId - beforeMaxId);

            // Use the smaller value to avoid false positives from AUTO_INCREMENT gaps
            // But if full row data is available (or captureFullRows is enabled), use count-based calculation
            long newRows;
            // When captureFullRows is enabled, always use count-based calculation
            // because MAX(ID) doesn't work for string primary keys
            if (captureFullRows) {
                newRows = countBasedNewRows;
            } else if (beforeSnap != null && afterSnap != null && beforeSnap.hasRowData() && afterSnap.hasRowData()) {
                // With full row data, count-based is more accurate
                newRows = countBasedNewRows;
            } else {
                // Without full row data, use the smaller of count vs ID difference
                newRows = Math.min(countBasedNewRows, idBasedNewRows);
            }
            diff.setNewRowCount(table, newRows);

            // Calculate deleted rows based on count difference
            long deletedRows = Math.max(0, beforeCount - afterCount + newRows);
            diff.setDeletedRowCount(table, deletedRows);

            // Fetch actual new row data if there are new rows
            if (newRows > 0) {
                List<Map<String, Object>> newRowsData;
                // When full row data is available, compute new rows from row data comparison
                // This handles string primary keys correctly (where MAX() doesn't work)
                if (afterSnap != null && afterSnap.hasRowData()) {
                    // After snapshot has row data - compute new rows by comparing with before
                    // This works even if before snapshot is empty (new table scenario)
                    newRowsData = computeNewRowsFromRowData(
                        beforeSnap != null ? beforeSnap : new TableSnapshot(table),
                        afterSnap);
                } else {
                    newRowsData = fetchNewRows(table, idColumn, beforeMaxId, afterMaxId);
                }
                diff.setNewRowsData(table, newRowsData);
            }

            // Compute modifications if full row data is available
            if (beforeSnap != null && afterSnap != null
                && beforeSnap.hasRowData() && afterSnap.hasRowData()) {
                computeModifications(diff, table, idColumn, beforeSnap, afterSnap);
            }

            log.debug("Diff for {}: newRows={}, deletedRows={}, modifiedRows={}, idColumn={}",
                table, newRows, deletedRows, diff.getModifiedRowCount(table), idColumn);
        }

        return diff;
    }

    /**
     * Gets the MAX(ID) for a table.
     */
    private Long getMaxId(String table, String idColumn) {
        try {
            String sql = "SELECT MAX(" + idColumn + ") FROM " + table;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.warn("Failed to get max ID for table {} (column {}): {}", table, idColumn, e.getMessage());
            return 0L;
        }
    }

    /**
     * Gets the row count for a table.
     */
    private Long getRowCount(String table) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.warn("Failed to get row count for table {}: {}", table, e.getMessage());
            return 0L;
        }
    }

    /**
     * Fetches new rows between the before and after max IDs.
     */
    private List<Map<String, Object>> fetchNewRows(String table, String idColumn, long beforeMaxId, long afterMaxId) {
        try {
            String sql = "SELECT * FROM " + table +
                " WHERE " + idColumn + " > ? AND " + idColumn + " <= ?" +
                " ORDER BY " + idColumn;
            return jdbcTemplate.queryForList(sql, beforeMaxId, afterMaxId);
        } catch (Exception e) {
            log.warn("Failed to fetch new rows for table {}: ", table, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Computes new rows by comparing before and after row data.
     * This method works correctly with any primary key type (including strings).
     */
    private List<Map<String, Object>> computeNewRowsFromRowData(TableSnapshot beforeSnap, TableSnapshot afterSnap) {
        List<Map<String, Object>> newRows = new ArrayList<>();
        Set<Object> beforeKeys = beforeSnap.getRowsByPrimaryKey().keySet();

        for (Map.Entry<Object, Map<String, Object>> entry : afterSnap.getRowsByPrimaryKey().entrySet()) {
            if (!beforeKeys.contains(entry.getKey())) {
                newRows.add(entry.getValue());
            }
        }

        return newRows;
    }

    /**
     * Fetches all rows indexed by primary key.
     */
    private Map<Object, Map<String, Object>> fetchAllRowsIndexedByPK(String table, String idColumn) {
        Map<Object, Map<String, Object>> result = new LinkedHashMap<>();

        try {
            String sql = "SELECT * FROM " + table + " ORDER BY " + idColumn;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            int limit = Math.min(rows.size(), maxRowsToCapture);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> row = rows.get(i);
                Object pkValue = getValueCaseInsensitive(row, idColumn);
                if (pkValue != null) {
                    result.put(pkValue, row);
                }
            }

            if (rows.size() > maxRowsToCapture) {
                log.warn("Table {} has {} rows, only capturing first {} for modification detection",
                    table, rows.size(), maxRowsToCapture);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch rows for table {}: {}", table, e.getMessage());
        }

        return result;
    }

    /**
     * Gets the underlying JdbcTemplate.
     */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * Computes row modifications by comparing before/after row data.
     */
    private void computeModifications(SnapshotDiff diff, String table, String idColumn,
                                      TableSnapshot beforeSnap,
                                      TableSnapshot afterSnap) {
        Map<Object, Map<String, Object>> beforeRows = beforeSnap.getRowsByPrimaryKey();
        Map<Object, Map<String, Object>> afterRows = afterSnap.getRowsByPrimaryKey();

        List<RowModification> modifications = new ArrayList<>();

        for (Map.Entry<Object, Map<String, Object>> beforeEntry : beforeRows.entrySet()) {
            Object pk = beforeEntry.getKey();
            Map<String, Object> beforeRow = beforeEntry.getValue();
            Map<String, Object> afterRow = afterRows.get(pk);

            // Row exists in both - check if modified
            if (afterRow != null && !rowsEqual(beforeRow, afterRow)) {
                modifications.add(new RowModification(pk, beforeRow, afterRow));
            }
        }

        diff.setModifiedRowCount(table, modifications.size());
        diff.setModifiedRowsData(table, modifications);
    }

    /**
     * Compares two rows for equality.
     */
    private boolean rowsEqual(Map<String, Object> row1, Map<String, Object> row2) {
        if (row1.size() != row2.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : row1.entrySet()) {
            String key = entry.getKey();
            Object val1 = entry.getValue();
            Object val2 = getValueCaseInsensitive(row2, key);
            if (!valuesEqual(val1, val2)) {
                return false;
            }
        }
        return true;
    }

    private boolean valuesEqual(Object v1, Object v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        if (v1 instanceof Number && v2 instanceof Number) {
            return ((Number) v1).doubleValue() == ((Number) v2).doubleValue();
        }
        return v1.equals(v2);
    }

    private Object getValueCaseInsensitive(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isSystemSchema(String tableSchema, String activeSchema) {
        if (tableSchema == null || tableSchema.isEmpty()) {
            return false;
        }
        if (activeSchema != null && tableSchema.equalsIgnoreCase(activeSchema)) {
            return false;
        }
        String schema = tableSchema.toUpperCase(Locale.ROOT);
        return schema.equals("INFORMATION_SCHEMA")
            || schema.equals("PG_CATALOG")
            || schema.equals("MYSQL")
            || schema.equals("SYS")
            || schema.equals("SYSTEM")
            || schema.equals("PERFORMANCE_SCHEMA");
    }
    
    /**
     * Clears the primary key detection cache.
     * Useful for testing or when database schema changes.
     */
    public void clearPrimaryKeyCache() {
        tablePrimaryKeyCache.clear();
    }
}
