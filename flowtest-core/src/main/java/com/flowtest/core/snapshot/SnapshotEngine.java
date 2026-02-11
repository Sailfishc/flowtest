package com.flowtest.core.snapshot;

import com.flowtest.core.fixture.EntityMetadata;
import com.flowtest.core.util.ValueComparator;
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
        return takeBeforeSnapshot(tables, Collections.<String, String>emptyMap(), Collections.<String, Object>emptyMap());
    }

    /**
     * Takes a "before" snapshot of the given tables with sharding key filters.
     *
     * @param tables the table names to snapshot
     * @param shardingKeyColumns map of table name to sharding key column name
     * @param shardingKeyValues map of table name to sharding key value
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeBeforeSnapshot(Set<String> tables,
                                                          Map<String, String> shardingKeyColumns,
                                                          Map<String, Object> shardingKeyValues) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            String tableKey = table.toLowerCase();
            String idColumn = getIdColumnForTable(table);
            String shardingColumn = shardingKeyColumns.get(tableKey);
            Object shardingValue = shardingKeyValues.get(tableKey);
            
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setRowCount(getRowCount(table, shardingColumn, shardingValue));

            // Capture full row data indexed by primary key
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table, idColumn, shardingColumn, shardingValue);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("Before snapshot for {}: rowCount={}, rowDataSize={}, idColumn={}, shardingKey={}:{}",
                table, snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size(), idColumn, shardingColumn, shardingValue);
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
        return takeAfterSnapshot(tables, Collections.<String, String>emptyMap(), Collections.<String, Object>emptyMap());
    }

    /**
     * Takes an "after" snapshot of the given tables with sharding key filters.
     *
     * @param tables the table names to snapshot
     * @param shardingKeyColumns map of table name to sharding key column name
     * @param shardingKeyValues map of table name to sharding key value
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeAfterSnapshot(Set<String> tables,
                                                         Map<String, String> shardingKeyColumns,
                                                         Map<String, Object> shardingKeyValues) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            String tableKey = table.toLowerCase();
            String idColumn = getIdColumnForTable(table);
            String shardingColumn = shardingKeyColumns.get(tableKey);
            Object shardingValue = shardingKeyValues.get(tableKey);
            
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setRowCount(getRowCount(table, shardingColumn, shardingValue));

            // Capture full row data indexed by primary key
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table, idColumn, shardingColumn, shardingValue);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("After snapshot for {}: rowCount={}, rowDataSize={}, idColumn={}, shardingKey={}:{}",
                table, snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size(), idColumn, shardingColumn, shardingValue);
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

            long beforeCount = beforeSnap != null && beforeSnap.getRowCount() != null ? beforeSnap.getRowCount() : 0;
            long afterCount = afterSnap != null && afterSnap.getRowCount() != null ? afterSnap.getRowCount() : 0;

            // Calculate new rows based on row count difference
            long newRows = Math.max(0, afterCount - beforeCount);
            diff.setNewRowCount(table, newRows);

            // Calculate deleted rows based on count difference
            long deletedRows = Math.max(0, beforeCount - afterCount + newRows);
            diff.setDeletedRowCount(table, deletedRows);

            // Compute new rows data from row data comparison (works with any PK type)
            if (newRows > 0 && afterSnap != null && afterSnap.hasRowData()) {
                List<Map<String, Object>> newRowsData = computeNewRowsFromRowData(
                    beforeSnap != null ? beforeSnap : new TableSnapshot(table),
                    afterSnap);
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
     * Gets the row count for a table.
     */
    private Long getRowCount(String table) {
        return getRowCount(table, null, null);
    }

    /**
     * Gets the row count for a table with optional sharding key filter.
     */
    private Long getRowCount(String table, String shardingColumn, Object shardingValue) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
            if (shardingColumn != null && shardingValue != null) {
                sql.append(" WHERE ").append(shardingColumn).append(" = ?");
                return jdbcTemplate.queryForObject(sql.toString(), Long.class, shardingValue);
            }
            return jdbcTemplate.queryForObject(sql.toString(), Long.class);
        } catch (Exception e) {
            log.warn("Failed to get row count for table {}: {}", table, e.getMessage());
            return 0L;
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
        return fetchAllRowsIndexedByPK(table, idColumn, null, null);
    }

    /**
     * Fetches all rows indexed by primary key with optional sharding key filter.
     */
    private Map<Object, Map<String, Object>> fetchAllRowsIndexedByPK(String table, String idColumn,
                                                                      String shardingColumn, Object shardingValue) {
        Map<Object, Map<String, Object>> result = new LinkedHashMap<>();

        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
            List<Map<String, Object>> rows;
            
            if (shardingColumn != null && shardingValue != null) {
                sql.append(" WHERE ").append(shardingColumn).append(" = ?");
                sql.append(" ORDER BY ").append(idColumn);
                rows = jdbcTemplate.queryForList(sql.toString(), shardingValue);
            } else {
                sql.append(" ORDER BY ").append(idColumn);
                rows = jdbcTemplate.queryForList(sql.toString());
            }

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
        return ValueComparator.valuesEqual(v1, v2);
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
     * Finds primary key values that exist in the after snapshot but not in the before snapshot.
     * This method works with any primary key type (numeric, UUID, string, etc.).
     *
     * @param before the before snapshot
     * @param after the after snapshot
     * @return list of new primary key values
     */
    public List<Object> findNewPrimaryKeys(TableSnapshot before, TableSnapshot after) {
        List<Object> newKeys = new ArrayList<>();
        if (after == null || !after.hasRowData()) {
            return newKeys;
        }

        Set<Object> beforeKeys = before != null && before.hasRowData()
            ? before.getRowsByPrimaryKey().keySet()
            : Collections.<Object>emptySet();

        for (Object key : after.getRowsByPrimaryKey().keySet()) {
            if (!beforeKeys.contains(key)) {
                newKeys.add(key);
            }
        }

        return newKeys;
    }

    /**
     * Deletes rows from a table by their primary key values.
     * Supports any primary key type (numeric, UUID, string, etc.).
     *
     * @param table the table name
     * @param idColumn the primary key column name
     * @param pkValues the primary key values to delete
     * @return total number of rows deleted
     */
    public int deleteRowsByPrimaryKeys(String table, String idColumn, List<Object> pkValues) {
        if (pkValues == null || pkValues.isEmpty()) {
            return 0;
        }

        int totalDeleted = 0;

        // Delete in batches to avoid overly large IN clauses
        int batchSize = 100;
        for (int i = 0; i < pkValues.size(); i += batchSize) {
            int end = Math.min(i + batchSize, pkValues.size());
            List<Object> batch = pkValues.subList(i, end);

            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(table).append(" WHERE ").append(idColumn).append(" IN (");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) {
                    sql.append(", ");
                }
                sql.append("?");
            }
            sql.append(")");

            try {
                int deleted = jdbcTemplate.update(sql.toString(), batch.toArray());
                totalDeleted += deleted;
            } catch (Exception e) {
                log.warn("Failed to delete rows from {} by primary keys: {}", table, e.getMessage());
            }
        }

        return totalDeleted;
    }

    /**
     * Clears the primary key detection cache.
     * Useful for testing or when database schema changes.
     */
    public void clearPrimaryKeyCache() {
        tablePrimaryKeyCache.clear();
    }
}
